# liveness-api (frozen skeleton, #13)

Public types the registration clients use. No model or UI types here. Java 11.
Full API docs (#69) will expand this. Every public type is in `liveness-api/src/main/java/io/sajeev/api/`.

| Type | What it is |
|---|---|
| `LivenessService` | Facade: `start(Workflow, PolicyConfig)` → `LivenessSession` |
| `LivenessSession` | One check: `onFrame`, `state`, `verdict`, `abort`. States: PASSIVE, ACTIVE, DONE, ABORTED |
| `FaceCaptureDevice` | Device SPI (IN1): `discover`, `info`, `capabilities`, `openStream`, `capture`, `close` |
| `FrameSource` | Frames from an open stream: `next(timeoutMillis)`, `health()` |
| `Frame` | JPEG bytes + monotonic `arrivalNanos` + `sequence`. Immutable |
| `StreamHealth`, `DeviceCapabilities`, `DeviceInfo` | Device and stream facts. `DeviceInfo.Level` is L0 or L1 |
| `DeviceException` | Carries an `ErrorState` |
| `PadVoter` | One inspector: `name()`, `vote(List<Frame>)` |
| `Vote` | LIVE / ATTACK / ABSTAIN + probability + reason. ABSTAIN never counts as LIVE |
| `GateResult` | Decision Gate output: LIVE_PASSIVE, ACTIVE_REQUIRED, ATTACK_SUSPECTED, CANNOT_JUDGE |
| `Verdict` | Final tier: LIVE_PASSIVE, LIVE_ACTIVE, ACTIVE_REQUIRED, ATTACK_SUSPECTED, CANNOT_JUDGE, LIMITED + optional `ErrorState` |
| `ErrorState` | The 13 PDF error states (Bible §9) |
| `ChallengeType` | The 10 active challenge types (Bible §6.6) |
| `Workflow` | RESIDENT, OPERATOR, SUPERVISOR |
| `PolicyConfig` | Passive threshold, min challenges, max retries. Values come from config |

**Frozen:** any change to this list needs a note in `BUILD_LOG.md`.

## Device interface {#device}

`FaceCaptureDevice` (IN1): `discover()`, `info()`, `capabilities()`, `openStream()` returning a `FrameSource`, `capture()`, `close()`. Implementations: `Sbi10HttpAdapter` (SBI 1.0 over HTTP, tested against the unmodified MockMDS), `Sbi095HttpAdapter` (0.9.5, recorded responses), `AndroidSbiStreamAdapter` and `AndroidSbiCaptureOnlyAdapter` (not built yet), `FileFrameSource` (MOCK replay of recorded frames). Device errors carry an `ErrorState` (`DeviceException`). `docs/INTEROP_RESULTS.md` lists what was checked.

## Engine interface {#engine}

```java
LivenessEngine engine = new LivenessEngine(new EngineModels(modelDir), config, EngineSettings.defaults(), auditLog, metrics);
EngineSession s = engine.start(Workflow.RESIDENT, new PolicyConfig(0.80, 2, 3));
s.onFrame(new Frame(jpeg, System.nanoTime(), sequence));   // never blocks
// poll s.state(), s.verdict(), s.currentChallenge(), s.liveGuidance()
Binding.Result r = s.checkCapture(pngOrJpeg, System.nanoTime(), captureTimeoutMillis);
PolicyEngine.AttemptResult result = s.attemptResult();      // PASSED only for live AND bound
```

`LivenessSession` (frozen) has `onFrame`, `state`, `verdict`, `abort`. `EngineSession` adds `currentChallenge`, `markPrompted`, `liveGuidance`, `checkCapture`, `bindingResult`, `attemptResult`, `passiveVotes`. The extra calls are not on the frozen interface. The state machine is PASSIVE, ACTIVE, DONE or ABORTED; Binding runs when the client calls `checkCapture`. Models load once per engine and are shared by all sessions; `LivenessEngine.close()` releases them.

## Client SPI (desktop, in the registration-client patch)

`LivenessFacade.openSession(workflow)` returns a `LivenessSessionHandle` (`onFrame`, `progress`, `checkCapture`, `close`); with no provider it returns a session that is UNAVAILABLE for its whole life.
