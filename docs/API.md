# API documentation

SAJEEV exposes no network service of its own. Everything below is an **in-process** API: a Java library on the desktop JVM and in the Android app, a Java SPI inside the Desktop Registration Client, and a Pigeon (platform channel) API between Flutter and Java on Android. The only HTTP calls are the standard MOSIP SBI calls the clients already make to the capture device.

| API | Where | Who calls it | Authentication |
|---|---|---|---|
| [Engine API](#engine) | `liveness-core` (`io.sajeev.engine`) | both clients, the bench, the evaluation | none: in-process call |
| [Frozen types](#types) | `liveness-api` (`io.sajeev.api`) | everything | none |
| [Desktop SPI](#desktop-spi) | `registration-api` (`io.mosip.registration.api.liveness`), patch 0001 | Desktop controllers | none: Spring beans in one JVM |
| [Android API](#android) | `pigeon/liveness.dart`, patches 0006 and 0007 | Flutter screens | none: platform channel inside one app |
| [Device interface and SBI calls](#device) | `liveness-api` + `devices` | the engine's adapters, the client's existing SBI code | device responses are signed by the device (SBI digital ID); the Android frame feed is guarded by a signature-level permission and a caller check |
| [Configuration](#config) | `mosip.registration.liveness.*` keys | the client's global parameters | server-synced; local overrides refused on Android |
| [Audit events](#audit) | each client's audit framework | the clients | the client's own audit log |

<a id="engine"></a>

## Engine API

```java
// once per process: models are verified against the signed manifest, then loaded
EngineModels models = EngineModels.loadVerified(modelDir, EngineModels.bundledTrustRoot(), 0);
LivenessEngine engine = new LivenessEngine(models, Config.load(clientProperties), EngineSettings.defaults(),
        new AuditLog(System::currentTimeMillis), new LivenessMetrics());

// once per person and attempt
EngineSession s = engine.start(Workflow.RESIDENT);          // policy (threshold, challenges, retries) from the configuration
s.onFrame(new Frame(jpegBytes, System.nanoTime(), seq++));  // for every frame of the device stream; never blocks
ChallengeType prompt = s.currentChallenge();                // show challenge.<type> when not null
ErrorState hint = s.liveGuidance();                         // show the guidance text for it when not null
Verdict v = s.verdict();                                    // null while running

// when v is LIVE_PASSIVE or LIVE_ACTIVE: let the device capture, then bind (works once per live verdict)
Binding.Result r = s.checkCapture(capturedJpegOrPng, System.nanoTime(), captureTimeoutMillis);
boolean store = r.verdict() == Binding.Verdict.BOUND;
s.abort();                                                  // releases every frame
```

| Method | Returns | Notes |
|---|---|---|
| `LivenessEngine.start(Workflow)` | `EngineSession` | Policy for that workflow from the configuration; at most 16 sessions at once (`EngineSettings.maxConcurrentSessions`), the next ends at once with `ENGINE_ERROR` |
| `LivenessEngine.start(Workflow, PolicyConfig)` | `EngineSession` | Explicit policy (tests, bench) |
| `LivenessEngine.policyFor(Workflow)` | `PolicyConfig` | Threshold, minimum challenges, retries after per-workflow overrides |
| `EngineSession.onFrame(Frame)` | — | Bounded queue; the oldest frame is dropped under load (not in `deterministic` mode) |
| `EngineSession.state()` | `PASSIVE`, `ACTIVE`, `DONE`, `ABORTED` | |
| `EngineSession.verdict()` | `Verdict` or null | See tiers below |
| `EngineSession.currentChallenge()` | `ChallengeType` or null | The prompt to show now |
| `EngineSession.markPrompted(nowNanos, lastSeq)` | — | Only when `EngineSettings.autoPrompt` is false: the UI says when the prompt became visible |
| `EngineSession.liveGuidance()` | `ErrorState` or null | Why the newest frames are not usable (no face, many faces, poor quality, bad frames) |
| `EngineSession.checkCapture(bytes, arrivalNanos, captureTimeoutMs)` | `Binding.Result` | `BOUND` only after a live verdict, for one capture, with exactly one face that is the person seen live, in time |
| `EngineSession.attemptResult()` | `PolicyEngine.AttemptResult` | `PASSED` only for live **and** bound |
| `EngineSession.diagnostics(mode, audience)` | ordered map | Operator-only troubleshooting values when an administrator enabled diagnostic mode; never images or identifiers |
| `EngineSession.awaitIdle(ms)` | boolean | Evaluation and bench only: wait until the queue is empty |
| `EngineSession.abort()` | — | Ends the session as `CANNOT_JUDGE` / `aborted` |

**Verdict tiers:** `LIVE_PASSIVE` and `LIVE_ACTIVE` (the device may capture; Binding decides storing) · `ACTIVE_REQUIRED` (challenge failed or timed out) · `ATTACK_SUSPECTED` (presentation attack: generic message only) · `CANNOT_JUDGE` (device, stream or engine problem: guidance, retry) · `LIMITED` (single-image check only, or active liveness switched off: never shown as full liveness).

**Errors** (`ErrorState`, the PS4 list): `DEVICE_UNAVAILABLE`, `DEVICE_DISCONNECTED`, `CAMERA_UNAVAILABLE`, `FACE_NOT_DETECTED`, `MULTIPLE_FACES`, `POOR_QUALITY`, `ACTIVE_REQUIRED`, `PAD_FAIL`, `CHALLENGE_FAILED`, `CHALLENGE_TIMEOUT`, `MAX_RETRIES`, `INVALID_FRAME_DATA`, `ENGINE_ERROR`. Each maps to a message key (`GuidanceKeys.keyFor`) with texts in six languages. No error is ever a pass.

<a id="types"></a>

## Frozen types

Every public type is in `liveness-api/src/main/java/io/sajeev/api/` (Java 11, immutable DTOs, no model or UI types). A change to this list needs a note in `BUILD_LOG.md`.

| Type | What it is |
|---|---|
| `LivenessService`, `LivenessSession` | Engine facade and one check (`onFrame`, `state`, `verdict`, `abort`) |
| `FaceCaptureDevice`, `FrameSource` | Device SPI (see below) and its frame stream (`next(timeoutMillis)`, `health()`) |
| `Frame` | JPEG bytes, monotonic `arrivalNanos`, `sequence` |
| `DeviceInfo` (`Level` L0/L1), `DeviceCapabilities`, `StreamHealth`, `DeviceException` | Device facts; the exception carries an `ErrorState` |
| `PadVoter`, `Vote` | One voter; LIVE / ATTACK / ABSTAIN + probability + reason |
| `GateResult`, `Verdict`, `ErrorState` | Decision outputs |
| `ChallengeType` | `BLINK`, `SMILE`, `TURN_HEAD_LEFT`, `TURN_HEAD_RIGHT`, `LOOK_LEFT`, `LOOK_RIGHT`, `LOOK_UP`, `LOOK_DOWN`, `EYES_CLOSED_HOLD`, `OPEN_MOUTH` |
| `Workflow`, `PolicyConfig` | `RESIDENT`, `OPERATOR`, `SUPERVISOR`; threshold, minimum challenges, retries |

<a id="desktop-spi"></a>

## Desktop SPI (registration-api)

Added by patch 0001, in the same style as MOSIP's existing `DocScannerFacade`. The stub implementation always answers UNAVAILABLE, so a client without SAJEEV refuses rather than passes.

```java
public interface LivenessSessionProvider {           // implemented by SajeevLivenessProvider (registration-services)
    String getServiceName();
    LivenessSessionHandle open(String workflow);       // "RESIDENT", "OPERATOR" or "SUPERVISOR"
}
public interface LivenessSessionHandle extends AutoCloseable {
    void onFrame(byte[] jpeg);
    LivenessProgress progress();   // phase RUNNING, LIVE, NOT_LIVE or UNAVAILABLE; challenge key; guidance key; reason
    LivenessCaptureCheck checkCapture(byte[] capturedImage, long captureTimeoutMillis);  // isBound(), getReason()
    void close();
}
```

`LivenessFacade.openSession(workflow)` picks the registered provider; with none, or when the provider throws, it returns a session that is UNAVAILABLE for its whole life. The controllers use it through `LivenessCaptureCoordinator`:

```java
coordinator.begin("RESIDENT");                   // per-workflow retry counter, session timeout
streamer.setFrameListener(coordinator::onFrame); // every frame of the device stream
switch (coordinator.pollBeforeCapture()) {       // every 200 ms on the JavaFX thread
    case START_CAPTURE: rCaptureTaskService(); break;      // live: let the device capture
    case WAIT: break;                                      // keep showing progress().getChallenge() / getGuidanceKey()
    default: showLivenessMessage(); break;                 // REFUSE_NOT_LIVE or REFUSE_UNAVAILABLE
}
Gate g = coordinator.decideStore(isoFaceRecord, LivenessClientConfig.captureTimeoutMillis()); // STORE only if bound
```

Operator login (`SessionContext.validateFace`), operator and supervisor authentication and EOD approval go through `LivenessAuthGate`, workflow OPERATOR or SUPERVISOR.

<a id="android"></a>

## Android API (Pigeon)

Defined in `pigeon/liveness.dart`, generated with the project's pinned pigeon 10.

```dart
class LivenessProgressDto { String phase; String? challengeKey; String? guidanceKey; String reason; bool mock; bool limited; }

@HostApi() abstract class LivenessApi {           // Java side: api_services/LivenessApi.java
  @async String start(String workflow);           // returns a session id
  LivenessProgressDto progress(String sessionId);
  @async bool checkCapture(String sessionId, Uint8List capturedImage, int captureTimeoutMs);
  void abort(String sessionId);
}
@FlutterApi() abstract class LivenessEvents {      // pushed on every change
  void onProgress(String sessionId, LivenessProgressDto progress);
}
```

Example (what `LivenessGate.passIfFace` does before the SBI capture intent is sent):

```dart
final id = await LivenessApi().start('RESIDENT');
LivenessEvents.setup(_Events((sid, p) {
  if (p.phase == 'LIVE') Navigator.pop(context, 'LIVE');         // capture may start
  // otherwise show livenessTexts[p.challengeKey ?? p.guidanceKey]
}));
```

The Java side refuses to store a face unless a live session exists and the capture is bound (`BiometricsDetailsApi.livenessAllowsStoring`), even if the Dart gate were skipped. Face login and approval spend a single-use ticket (`FaceAuthTickets`) after liveness, Binding and the 1:1 match; after `maxRetries` failures (default 3) face sign-in for that user is locked for five minutes (`FaceAuthLockout`), and the password path stays as configured.

<a id="device"></a>

## Device interface and SBI calls

`FaceCaptureDevice`: `discover()`, `info()`, `capabilities()` (measured fps, resolution, L0/L1, mock flag), `openStream()` → `FrameSource`, `capture()`, `close()`. Implementations: `Sbi10HttpAdapter`, `Sbi095HttpAdapter` (desktop SBI), `AndroidSbiStreamSource` (frame feed), `CameraXFrameSource` (tablet camera as an uncertified L0 device), `FileFrameSource` (tests). SBI error codes map to `ErrorState` in `SbiErrorMap`.

The SBI calls used (MOSIP SBI specification; ports 4501 to 4600 on localhost):

| Call | HTTP | Request body | Response |
|---|---|---|---|
| Discover | `MOSIPDISC /device` | `{"type":"Biometric Device","modality":"Face"}` | array of devices: `deviceId`, `deviceStatus`, `certification` (L0/L1), `serviceVersion`, `digitalId` (signed), `error` |
| Stream | `STREAM /stream` | `{"deviceId":"3","deviceSubId":"0","timeout":"10000"}` | `multipart/x-mixed-replace` of JPEG frames (M-JPEG) until the client stops reading |
| Capture | `RCAPTURE /capture` | `{"env":"Staging","purpose":"Registration","specVersion":"0.9.5","timeout":"10000","captureTime":"<ISO time>","transactionId":"<32 hex>","domainUri":"http://localhost","bio":[{"type":"Face","count":"1","bioSubType":["Full Face"],"requestedScore":"0","deviceId":"3","deviceSubId":"0",…}]}` | `{"biometrics":[{"specVersion":"0.9.5","data":"<signed JWT>","hash":"…","error":{"errorCode":"0"}}]}`; the JWT payload's `bioValue` is an ISO/IEC 19794-5 face record holding a JPEG 2000 image |

Recorded responses from MOSIP's mock devices are in `testdata/recorded-095/` and `testdata/recorded-android/`.

<a id="config"></a>

## Configuration

All keys start with `mosip.registration.liveness.` and accept a per-workflow suffix (`.resident`, `.operator`, `.supervisor`). Full list, defaults and the server-side snippet: [CONFIG.md](CONFIG.md). The most used: `enabled`, `policy.mode` (`HYBRID` or `ACTIVE_ALWAYS`), `passive.threshold`, `passive.minLiveVotes`, `active.challengeTypes`, `active.minChallenges`, `active.challengeTimeoutMs`, `maxRetries`, `onRepeatedFailure`.

<a id="audit"></a>

## Audit events

Own ID block, no clash with MOSIP's existing blocks (checked against `testdata/reference/mosip_audit_ids_1.2.0.x.txt`):

| ID | Event |
|---|---|
| REG-LIV-001 | Liveness check started |
| REG-LIV-002 | Passive liveness result (outcome and a coarse score band, never a raw score) |
| REG-LIV-003 | Active challenge prompted |
| REG-LIV-004 | Active challenge result |
| REG-LIV-005 | Captured face bound (or not) to the live face |
| REG-LIV-006 | Liveness verdict |
| REG-LIV-007 | Liveness switched off by configuration while a face was captured |
| REG-LIV-009 | Liveness engine unavailable |

No image, embedding or identifier is ever written to the audit log (`test_audit_never_contains_image_or_embedding`).
