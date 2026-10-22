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
