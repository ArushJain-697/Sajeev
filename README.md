# SAJEEV (सजीव)

*Seen live, then stored.* Face liveness and presentation-attack detection (PAD) for the MOSIP Desktop and Android Registration Clients (MOSIP Decode 2026, problem statement 4).

A face is stored only if the person was judged live **and** the captured face is the face that was seen live.

## What exists (24 Oct 2026)
- **Engine** (`liveness-core`, Java 11, also runs on Android): five PAD voters (texture CNN, moiré, flatness, frame edge, reflection), a deterministic Decision Gate, ten randomised challenges with re-evaluation (all voters keep running during a challenge), Binding (one face, embedding, geometry, timing), policy, audit, metrics. The safety laws are tested as properties and by mutation (`BUILD_LOG.md` #31). Settings follow the client's configuration, per workflow (resident, operator, supervisor).
- **Devices** (`devices`): SBI 1.0 and 0.9.5 adapters tested against MOSIP's mock devices; Android frame-feed, capture-only (labelled LIMITED) and CameraX sources.
- **Desktop client** (patches `0001`, `0005`; fork branch `sajeev/release-1.2.0.x`): resident capture, operator and supervisor face checks, configuration, English/French/Arabic texts, audit. Built and unit-tested with JDK 11. **Never run on a screen.**
- **Android client** (patches `0006`, `0007`; fork branch `sajeev/liveness`; `integrations/engine-android`): the engine as an AAR, Pigeon bridge, Flutter liveness widget, resident capture gate, **face login and face approval for operators and supervisors** (off by default), configuration, six languages, audit. The engine runs on an Android emulator and the debug APK builds. **Never run against a MOSIP server.**
- **Mock devices** (patches `0002`–`0004`): a frame feed for MOSIP's mock Android camera app and a replay mode with a chosen frame rate for MockMDS. Both say `mock: true`.
- **Bench** (`bench/run.sh`), **evaluation harness** (`eval/`: APCER/BPCER with Wilson intervals, baselines, sealed held-out run), **interoperability harness** (`interop/`).

## What does not exist yet (read this first)
- **No recordings of real people or real attacks.** Every liveness and attack result is synthetic or a still photo. No APCER or BPCER number exists and none should be quoted. All thresholds are starting values, not tuned.
- No real phone, no Windows machine, no MOSIP server login: neither client was run end to end. The Android settings, the frame-rate behaviour and the screens were checked by unit tests, an emulator and compile.
- Hindi, Kannada and Tamil texts are **machine translations** written for this project and not reviewed by native speakers; French and Arabic are unreviewed drafts.
- The MiniFASNet weights have no stated licence; the MediaPipe model card terms are unchecked (`DISCLOSURES.md`). The signing key is a development key.
- Hardware acceleration was tried (CoreML, NNAPI) and is **disabled**: neither matched the CPU's scores. The low-resource timings are from a Mac and an emulator, not a phone.

## Build and test
```
scripts/fetch_models.sh                      # downloads the model files (about 40 MB), see liveness-models/README.md
mvn -pl liveness-api,liveness-core,devices,bench,eval test -Dsajeev.models=liveness-models/models
bench/run.sh --clip testdata/fixtures-faces/one_face.jpg
```
Without the models the tests that need them skip (and say so). Docs: `docs/` (start with `TD_INDEX.md`). Per-ticket status, limits and numbers: `BUILD_LOG.md`. The four MOSIP forks and how they are built from the patches: `integrations/README.md`.

Licence: MPL-2.0 (`LICENSE`), third-party list in `DISCLOSURES.md`.
