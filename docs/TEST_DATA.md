# Test data

Everything needed to run the tests and the evaluation, where it is, where it came from, and what it may be used for.

## Accounts and credentials
**None are needed.** SAJEEV does not log in anywhere: the engine, the bench, the evaluation and the unit tests run offline. MOSIP's mock devices (MockMDS, MockAndroidSBI) need no account. The model files are fetched from a public GitHub release without credentials. The client patches were not run against a MOSIP server, so no operator, supervisor or machine credentials were used or are required to test them (their tests use mocks).

The only key in the project is the **model-signing key**: its public half is committed (`liveness-core/src/main/resources/model-signing-public.der`, a development key); the private half was never committed and is not needed to run anything. A deployment signs its own models with its own key (`docs/MODEL_UPDATES.md`).

## In the repository (`testdata/`)

| Path | What it is | Source | Used by |
|---|---|---|---|
| `fixtures-faces/one_face.jpg` | One face, 640x480 | NASA astronaut photo (public domain, scikit-image `data.astronaut()`), resized | detector, quality, engine and bench tests |
| `fixtures-faces/zero_faces.jpg`, `dark_one_face.jpg`, `blurry_one_face.jpg`, `two_faces_composite.jpg` | No face; 6% brightness; blurred; two faces side by side | generated from the photo above (SYNTHETIC) | FACE_NOT_DETECTED, POOR_QUALITY and MULTIPLE_FACES tests |
| `fixtures-gaze/*.jpg` | Neutral, look left/right/up/down, eyes closed | the same photo with the eyes edited (SYNTHETIC; left = the person's own left) | landmark feature and gaze tests |
| `clips-synthetic/fps3`, `fps10`, `fps15` | 280 frames: one face at three frame rates | generated | Frame Guard and frame-rate tests |
| `recorded-095/discovery-face.json`, `capture-face.json`, `stream-face.mjpeg` | SBI 0.9.5 discovery, capture and a 4.6 MB M-JPEG stream | recorded from MOSIP's MockMDS (the face inside is the mock device's stock image; provenance not verified, see `DISCLOSURES.md`) | SBI 0.9.5 adapter and interop tests |
| `recorded-android/rcapture-face.json` | Signed capture from the Android mock SBI | recorded from MOSIP's MockAndroidSBI | Android capture parsing tests |
| `reference/minifas_pytorch_ref.json` | Reference outputs of the original PyTorch MiniFASNet | produced by `export_compare.py` | ONNX equivalence test |
| `reference/mosip_audit_ids_1.2.0.x.txt` | Every audit ID in MOSIP's desktop client | read from the client source | test that the REG-LIV IDs never clash |

Synthetic fixtures are only for unit tests: they are not part of any evaluation manifest and never counted in a reported number.

## Outside the repository (not committed on purpose)

| What | Where | How to get or make it |
|---|---|---|
| Model files (YuNet, SFace, MediaPipe landmarks, MiniFASNet; about 40 MB) | `liveness-models/models/` after download | `scripts/fetch_models.sh` (SHA-256 pinned, then verified against the signed manifest); licences in `liveness-models/README.md` |
| Real recordings: 2 clips of the author (bona fide) and 4 attack clips (a phone showing a photo, a phone playing a video, three times) | the author's machine, `eval/clips/` after ingest (git-ignored) | faces of a real person: kept private. Record your own with `eval/RECORDING_PROTOCOL.md` and the consent form in `eval/consent/`; ingest with `eval/ingest.sh <session> <subject> <device> <folder>` |
| Held-out recordings | none yet | sealed by hash before any run (`eval/heldout.sh`, `planning/HELDOUT_PROTOCOL.md`) |

## To test with your own data
1. Record 10 s clips; name each file by what it is: `bona_…` for a real person, `P1_…` print, `P2_…` photo on a screen, `P3_…` video replay, `P4_…` live then swap, `P5_…` bent print, `P6_…` cut-out mask.
2. `eval/ingest.sh mysession s1 mycamera <folder>`
3. `eval/run_eval.sh --manifest eval/clips/mysession/manifest.csv --clips eval/clips/mysession --out metrics.json`: one line per clip with its verdict and votes, a tier histogram, APCER/BPCER with Wilson intervals.
4. `eval/tools/robustness_matrix.sh <workdir with src/*.mp4>`: the same clips at 2 resolutions and 4 frame rates.
