# Sequence diagrams

Desktop sequences match the code in patch 0005. Android sequences are the plan; those screens are not built.

## Resident {#resident} (desktop)

```mermaid
sequenceDiagram
  participant R as Resident
  participant UI as GenericBiometricsController
  participant Co as LivenessCaptureCoordinator
  participant E as Engine
  participant SBI as Face device
  UI->>SBI: open STREAM
  UI->>Co: begin(RESIDENT)
  loop each frame
    SBI-->>UI: JPEG frame
    UI->>Co: onFrame
    Co->>E: queue (drop oldest)
  end
  UI->>Co: pollBeforeCapture every 200 ms
  E-->>Co: guidance or challenge
  Co-->>UI: WAIT (show prompt)
  Co-->>UI: START_CAPTURE (live)
  UI->>SBI: RCAPTURE
  SBI-->>UI: ISO 19794-5 face
  UI->>Co: decideStore(face)
  Co->>E: Binding check
  alt live and bound
    Co-->>UI: STORE
    UI->>UI: validate, de-duplicate, store
  else any failure
    Co-->>UI: REFUSE (nothing stored, attempt counted)
  end
```

## Operator {#operator} (desktop)

```mermaid
sequenceDiagram
  participant O as Operator
  participant A as AuthenticationController
  participant B as BaseController.captureAndValidateFace
  participant Co as Coordinator
  participant SBI as Face device
  participant M as authValidator (local or online match)
  O->>A: face authentication
  A->>B: capture and validate
  B->>SBI: open STREAM
  B->>Co: begin(OPERATOR), frames, poll
  Co-->>B: live
  B->>SBI: close stream, RCAPTURE
  B->>Co: decideStore
  Co-->>B: STORE
  B->>M: match against onboarded face
  M-->>A: true or false
  Note over B,M: any refusal returns false before the match
```

## Supervisor {#supervisor} (desktop)

Same as operator with `begin(SUPERVISOR)`, used by the reviewer path of `AuthenticationController` and by `EODAuthenticationController`. The "supervisor physically present" wording: the check shows that a live person was in front of the camera; it does not prove who that person is, the existing face match does.

## Android resident {#android-resident}

Matches `LivenessGate`, `LivenessApi`, `AndroidLivenessController` and `BiometricsDetailsApi.parseRCaptureResponse` (patch 0006). Not run on a screen.

```mermaid
sequenceDiagram
  participant R as Resident
  participant D as Flutter (LivenessGate dialog)
  participant J as LivenessApi (Java)
  participant C as AndroidLivenessController
  participant F as SBI frame feed
  participant E as Engine
  participant B as BiometricsDetailsApi
  participant S as SBI app (intent)
  R->>D: tap Scan (Face)
  D->>J: start(RESIDENT)
  J->>C: start
  C->>F: bind + subscribe
  loop frames
    F-->>C: JPEG (ack each)
    C->>E: onFrame
    E-->>C: guidance / challenge
    C-->>D: progress event (main thread)
  end
  E-->>C: verdict LIVE
  C-->>J: progress LIVE (LiveGate.markLive)
  D->>B: invokeDiscoverSbi (SBI intents: discover, info, rcapture)
  B->>S: RCAPTURE intent
  S-->>B: ISO 19794-5 face (JPEG 2000)
  B->>J: consumeLiveAndBind (JP2 to PNG)
  J->>E: Binding check
  alt live and bound
    B->>B: store biometric in the registration
  else refused
    B-->>D: error LIVENESS_REFUSED (nothing stored)
  end
```

## Android operator login by face {#android-operator}

Matches `FaceAuthFlow`, `BiometricsDetailsApi.parseFaceAuthResponse`, `FaceAuthCheck`, `FaceAuthTicket` and `AuthenticationApi.loginWithFace`. Offline session only; needs `faceAuth.enabled = true`.

```mermaid
sequenceDiagram
  participant O as Operator
  participant L as Login page
  participant G as FaceAuthFlow
  participant B as BiometricsDetailsApi
  participant K as FaceAuthCheck
  participant T as FaceAuthTicket
  participant A as AuthenticationApi
  O->>L: user id, tap Login with face
  L->>G: verify(user, OPERATOR)
  G->>G: liveness dialog (as in the resident flow)
  G->>B: invokeDiscoverSbi("faceAuth:user", Face)
  B->>K: run(live and bound, 1:1 match with onboarded face)
  alt live, bound, matched
    K->>T: grant(user)
    B-->>G: MATCH
    G->>L: true
    L->>A: loginWithFace(user)
    A->>T: consume(user)  (single use, 60 s)
    A-->>L: offline session
  else any refusal
    B-->>G: NO_MATCH or LIVENESS_REFUSED
    G->>L: false (password login stays available)
  end
```

## Android supervisor approval {#android-supervisor}

Same as the operator login with `verify(user, SUPERVISOR)` from the approval dialog, and `PacketAuthenticationApi.authenticateWithFace(user)` spending the ticket instead of `loginWithFace`. The same dialog is used for the operator's post-registration authentication when the password is left empty (workflow OPERATOR).
