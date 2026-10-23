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

## Android (planned, not built)

Resident, operator and supervisor on Android follow the same three steps (liveness first, capture, Binding, then the existing step). The resident capture runs through an intent to the SBI app, so liveness must finish before the intent is fired and Binding runs when the result returns. Operator and supervisor face login are new screens (the clients are password-only today). No diagram is drawn for code that does not exist.
