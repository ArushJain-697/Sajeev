# SAJEEV (सजीव)

*Seen live, then stored.* Face liveness and presentation-attack detection (PAD) for the MOSIP Desktop and Android Registration Clients (MOSIP Decode 2026, problem statement 4).

A face is stored only if the person was judged live **and** the captured face is the face that was seen live.

## What exists (23 Oct 2026)
- **Engine** (`liveness-core`, Java 11, also runs on Android): five PAD voters (texture CNN, moire, flatness, frame edge, reflection), a deterministic Decision Gate, ten randomised challenges with re-evaluation, Binding (one face, embedding, geometry, timing), policy, audit, metrics. Safety laws are tested as properties and by mutation (`BUILD_LOG.md` #31).
- **Devices** (`devices`): SBI 1.0 and 0.9.5 adapters tested against MOSIP's mock devices; Android frame-feed adapters.
- **Desktop client**: patches `0001` and `0005` (registration-api SPI, resident capture, operator and supervisor face checks, config, languages, audit). Built and unit-tested on `release-1.2.0.x` with JDK 11; **not run on a screen**.
- **Android client**: patches `0006` and `0007` plus `integrations/engine-android`: engine as an AAR running on the emulator, Pigeon bridge, Flutter widget with golden images, resident capture gate, config and audit. **Face login and approval screens are not built.**
- **Bench** (`bench/run.sh`): runs the engine on a clip. **Eval harness** (`eval/run_eval.sh`): APCER/BPCER with Wilson intervals.

## What does not exist yet (read this first)
- **No recordings of real people or real attacks.** Every liveness and attack result so far is synthetic or a still photo. No APCER or BPCER number exists and none should be quoted. All thresholds are starting values.
- No real phone, no Windows machine, no MOSIP server login: the clients were never run end to end.
- French and Arabic texts are unreviewed; Hindi, Kannada and Tamil are English placeholders.
- MiniFASNet weights have no stated licence; the MediaPipe model card terms are unchecked (see `DISCLOSURES.md`).

## Build and test
```
mvn -pl liveness-api,liveness-core,devices,bench,eval test -Dsajeev.models=<model dir>
bench/run.sh --clip testdata/fixtures-faces/one_face.jpg
```
The model files are not in the repository (licences above). Docs: `docs/` (start with `TD_INDEX.md`). Per-ticket status, limits and numbers: `BUILD_LOG.md`.

Licence: MPL-2.0 (`LICENSE`), third-party list in `DISCLOSURES.md`.
