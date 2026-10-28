# SAJEEV: face liveness and presentation-attack detection for the MOSIP Registration Clients

MOSIP Decode 2026 · Problem statement 4 (complexity: high) · *Seen live, then stored.*

## The problem
Today the Desktop and Android Registration Clients accept any face the camera sees: a printed photo, a photo on a phone, or a video replay. The same is true for operator and supervisor face authentication. PS4 asks both clients to establish that the face belongs to a live person, using the L0/L1 device's frame stream: passive liveness first, then an automatic, unpredictable active challenge when the passive result is not good enough, with configuration, error handling, audit, and online and offline operation.

## What SAJEEV adds
- **One engine for both clients** (`liveness-core`, Java 11, runs on the desktop JVM and inside the Android app as an AAR). Fully offline: models are signed and verified before loading; no network calls.
- **Passive stage:** six voters vote LIVE, ATTACK or ABSTAIN: a texture CNN (MiniFASNet), moiré, 3D flatness (homography over landmarks), frame edge, screen/print reflection, and eyelid motion. A deterministic Decision Gate decides; two ATTACK votes always fail; only voters that give **evidence of life** (texture, flatness, eyelid motion) can make up a passive pass, so "no attack seen" is never treated as "alive".
- **Active stage:** challenges drawn with `SecureRandom` from a configurable pool (blink, smile, turn head, look up/down/left/right, eyes closed, open mouth), filtered by the camera's measured frame rate, re-drawn on every attempt; every PAD voter keeps running during the challenge; the action must start after the prompt.
- **Binding** (our addition): the stored capture must be the same single face that was seen live, at the same moment (embedding, landmark geometry, timing). This closes the gap between a live preview and a swapped capture.
- **Policy per workflow:** residents use the PS4 hybrid flow; operators and supervisors always get the challenge (`ACTIVE_ALWAYS`) with three challenges. Thresholds, pools, counts, timeouts, retries and behaviour on repeated failure are configurable from each client's configuration.
- **Client integrations** (as forks with pull requests): Desktop (resident capture, operator login and authentication, supervisor/EOD approval, operator onboarding, exception photo) and Android (resident capture, new operator face login and supervisor face approval, onboarding, exception photo), with the PS4 messages in six languages, retry limits, lockout and audit events.
- **Mock devices** for development and tests: a frame-feed service for MOSIP's mock Android SBI and a replay mode for MockMDS.
- **Evidence tooling:** a reproducible evaluation harness (APCER/BPCER with Wilson intervals, ISO/IEC 30107-3 style), a sealed held-out gate, a robustness matrix, replay and fuzz harnesses, an interoperability harness, 274 automated tests.

## What we measured (one person, one room, one webcam: behaviour, not rates)
- **Real clips of the person:** 57 of 64 presentations (2 clips x 8 camera settings) passed passively; the rest went to the challenge.
- **Phone showing a photo:** never accepted at any of 8 camera settings.
- **Phone playing a video of the person:** not accepted in 18 of 24 presentations; **accepted by the passive gate in 6 of 24** (a video shows a real moving head and real eyelids). Operators and supervisors always face the challenge; residents can be switched to `ACTIVE_ALWAYS` by configuration.
- **Replay of a recording that does not see the prompt, against the challenge:** about 2% to 5% passed three challenges by chance (Wilson 95% upper bounds 8.7% to 12.2%).
- **Red teams:** two rounds (seven reviewers in the second) found and fixed, among others, a texture preprocessing bug that rejected every real face, a configuration that never reached the engine, unprotected login and onboarding paths, and non-reproducible evaluation runs. Every finding and fix is in `BUILD_LOG.md`.

## Honest limits
No printed-photo attack has been recorded; data comes from one person; no APCER/BPCER should be quoted. Neither client was run end to end against a MOSIP server (machine registration was not done). The MiniFASNet weights have no stated licence. Hindi, Kannada and Tamil texts are machine translations. Not tested: 3D masks, injected video, a live attacker who reacts to the prompt.

## Run it
`scripts/fetch_models.sh`, then `mvn -pl liveness-api,liveness-core,devices,bench,eval test -Dsajeev.models=$PWD/liveness-models/models`, then `bench/run.sh --clip <frames or clip>`. Details: `docs/INSTALL.md`; design: `docs/TD_INDEX.md` (TD1 to TD16); UI states: `docs/UIUX/`; tests: `docs/TEST_CASES.md`.

## Links
- Engine, docs, evaluation: https://github.com/ArushJain-697/Sajeev
- Desktop client fork (PR #1, base `release-1.2.0.x`): https://github.com/ArushJain-697/registration-client/pull/1
- Android client fork (PR #1, base `develop`): https://github.com/ArushJain-697/android-registration-client/pull/1
- Mock Android SBI frame feed (PR #1): https://github.com/ArushJain-697/android-camera-mds/pull/1
- MockMDS replay (PR #1): https://github.com/ArushJain-697/mosip-mock-services/pull/1
