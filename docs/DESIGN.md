# Design

How SAJEEV decides. Everything here describes code that exists in `liveness-core` (23 Oct 2026). Numbers marked **TARGET** are starting values that have not been tuned on recordings yet.

Principle: **seen live, then stored.** A face is stored only if the person was judged live AND the stored capture is the face seen live.

## Decision flow {#decision}

1. **Frame Guard** checks the stream (rate, bad frames, stalls). Below the configured minimum fps the result is a device-inadequate guidance, never a pass.
2. **Face tracker** needs exactly one usable face (size, brightness, sharpness). Otherwise the person gets guidance (`FACE_NOT_DETECTED`, `MULTIPLE_FACES`, `POOR_QUALITY`); this does not use up a retry.
3. After 8 usable frames (TARGET) five **voters** look at the window. Each says LIVE, ATTACK or ABSTAIN ("I cannot judge", which never counts as live):

| Voter | Looks for | Weak spot |
|---|---|---|
| V1 texture | MiniFASNet CNN pair on the face crop | needs the model; weights licence not verified |
| V2 moire | isolated spectral peaks (screens) | small or heavily compressed faces |
| V3 flatness | RANSAC homography on 34 landmarks across a head turn | a bent print is not flat (known weakness) |
| V4 frame edge | straight edges framing the face | a tilted phone (axis-aligned only) |
| V5 reflection | glare and flat colour | extreme lighting |

4. **Decision Gate** (pure function, no model):

| Rule | Condition | Outcome |
|---|---|---|
| G1 | stream or face not usable | CANNOT_JUDGE: guidance, retry not used |
| G2 | two or more voters say ATTACK | PAD_FAIL: refused. No configuration can change this |
| G3 | no ATTACK, score >= threshold (0.80 TARGET), at least 3 LIVE votes, and the score still reaches the threshold when each ABSTAIN is counted at 0.5 | PASSIVE_PASS |
| G4 | anything else, active liveness enabled | ACTIVE_REQUIRED (strict when exactly one voter said ATTACK) |
| G5 | anything else, active liveness disabled | LIVENESS_NOT_ESTABLISHED: never accepted |

The "counted at 0.5" clause (decision D46) exists because a property test found that a voter abstaining could raise the score and turn a challenge into a pass.

5. **Active stage** (G4): a challenge sequence is drawn (below). **Re-evaluation:** the PAD voters keep running on the frames during the challenge; two ATTACK votes on one frame, or the strict voter saying ATTACK again, ends in PAD_FAIL. In strict mode the flagged voter must vote LIVE at some point during the challenge or the result is refused.
6. **Binding** (after the device capture): exactly one face, SFace embedding similarity to the live face, landmark-geometry continuity, and capture timing. All four must pass.
7. **Final rule** (`SessionAcceptance`): ACCEPT needs a live verdict AND a bound capture. A presentation attack anywhere in the session vetoes.

Safety laws, tested as properties over 10,000 generated cases each plus a mutation table (BUILD_LOG #31): **I1** no orphan capture, **I2** less evidence never helps, **I3** a veto wins.

## Challenges {#challenges}

- Ten types: blink, smile, turn left, turn right, look left, look right, look up, look down, close eyes and hold, open mouth.
- Drawn with `SecureRandom` per attempt: `active.minChallenges` (2) distinct types in random order; every retry draws a new sequence. The person never picks.
- Blink is only issued when the measured fps is at least the blink minimum (8 fps TARGET; to be set from the recall-vs-fps experiment #61); the others need the PDF minimum of 3 fps. If the pool is empty at this fps there is no attempt (LIMITED), never a silent pass.
- A challenge counts only on frames after the prompt (one-frame guard), within `active.challengeTimeoutMs` (5000).
- Gaze and closed-eye detectors are validated on synthetic images only so far.

## Errors and recovery {#errors}

| ErrorState | When | Person sees (English) | Retry used? |
|---|---|---|---|
| DEVICE_UNAVAILABLE / DEVICE_DISCONNECTED / CAMERA_UNAVAILABLE | no device, lost device, no camera | No camera device found / Device connection lost. Please reconnect. / Camera not available | no |
| FACE_NOT_DETECTED | no face for 10 s | Keep your face within the capture area | no |
| MULTIPLE_FACES | more than one face | Only one person should be in view | no |
| POOR_QUALITY | too small, dark or blurry | Move closer / Improve lighting / Hold still | no |
| ACTIVE_REQUIRED | active needed but not possible | We could not verify face liveness. Please try again. | yes |
| PAD_FAIL | gate G2 or attack during a challenge | Face verification could not be completed. Please try again. (no detail is shown) | yes |
| CHALLENGE_FAILED / CHALLENGE_TIMEOUT | strict voter not cleared; no action in time | ...Follow the prompted action. / ...Respond within the time shown. | yes |
| MAX_RETRIES | retry limit reached | ...Please follow the recovery process. | n/a |
| INVALID_FRAME_DATA | more than 20% bad frames | Device error. Please try again. | no |
| ENGINE_ERROR | model or engine failure | Face verification unavailable | never a pass |

After `maxRetries` (3) failures the default is BLOCK with the recovery guidance. Supervisor override and the biometric-exception flow are opt-in per workflow and always audited. English texts are the PDF's; French and Arabic are not reviewed by a native speaker.

## Out of scope / not built

The optional resident sub-policy by age group, alternative challenge pools for declared exceptions, a degradation ladder with classical-only mode. See `BUILD_LOG.md` for the exact limits of each ticket.

## Android frame feed: a note for the SBI maintainers (#70, reduced to one page by decision D42)

**Problem.** On Android the SBI app keeps the camera preview, and the client gets only the capture result through an intent. Liveness needs frames before the capture, so the client cannot judge liveness from the intent result alone.

**What we built (in the mock SBI only, patch `0003`).** A bound service `FrameFeedService` publishes JPEG frames to a subscribing client over a `Messenger`, while the SBI app keeps its own preview. Messages: subscribe, unsubscribe, frame (JPEG, sequence, monotonic time, `mock` flag), acknowledge, stats. The client acknowledges every frame; the service allows a few frames in flight per subscriber and drops frames for a slow one, so a slow client never stalls the SBI preview (tested: a 400 ms subscriber got 2.5 fps while discovery, info and capture kept working).

**Security.** The service needs a signature-level permission (`io.mosip.mock.sbi.permission.FRAME_FEED`), is not exported, and also checks the caller package against an allow-list. A second app signed with another key got `SecurityException: Not allowed to bind`.

**Compatibility.** An SBI app without the feed is still usable: the client falls back to a capture-only check, labelled LIMITED, which can never be shown as full liveness (`CaptureOnlyChecker`).

**What it is not.** This is not part of the MOSIP SBI specification and no SBI vendor implements it. A real device would need to add it. The `mock: true` flag is in the outer device-info JSON, not inside the signed token. A conformance test and a full proposal document are not written (`PROPOSAL_SBI_ANDROID_STREAM.md` stays a stub).
