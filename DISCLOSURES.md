# Disclosures (#02, #72)

Every third-party component the repo uses, plus what we only plan. "Ours" means written for SAJEEV. Status words: VERIFIED (licence file read on the source, 22 Oct 2026), UNVERIFIED (not confirmed yet), PLANNED (not a dependency yet).

## In use now
| Component | Licence | Source | Ours / borrowed | Status |
|---|---|---|---|---|
| ONNX Runtime (Java 1.19.2, Maven `com.microsoft.onnxruntime`) | MIT | github.com/microsoft/onnxruntime | borrowed | MIT recorded in the earlier audit; licence file not re-read in this run |
| Jackson databind 2.17.2 (devices module, SBI JSON) | Apache-2.0 | github.com/FasterXML/jackson-databind | borrowed | Apache-2.0 from the project's published licence |
| JUnit 5 (tests only) | EPL-2.0 | github.com/junit-team/junit5 | borrowed (test) | standard licence; not re-read in this run |
| YuNet face detector (OpenCV Zoo, `face_detection_yunet_2023mar.onnx`) | MIT | github.com/opencv/opencv_zoo, models/face_detection_yunet | borrowed | VERIFIED: LICENSE file read, "MIT License, Copyright (c) 2020 Shiqi Yu" |
| MediaPipe face landmarker model (`face_landmarker.task` → `face_landmarks_detector.onnx`, 478 points) | code Apache-2.0; **model card terms UNVERIFIED** | github.com/google-ai-edge/mediapipe | borrowed | UNVERIFIED: the official model card is a Google Drive preview that could not be read. Third-party mirrors say Apache-2.0. Confirm before any public release |
| SFace face recognition (`face_recognition_sface_2021dec.onnx`, the Binding embedding) | Apache-2.0 | github.com/opencv/opencv_zoo, models/face_recognition_sface | borrowed | VERIFIED: LICENSE file read (Apache License 2.0) on 23 Oct 2026 |
| MiniFASNet code (Silent-Face-Anti-Spoofing, V1 texture voter) | Apache-2.0 | github.com/minivision-ai/Silent-Face-Anti-Spoofing | borrowed | VERIFIED: repo licence Apache-2.0 |
| MiniFASNet weights (`2.7_80x80_MiniFASNetV2.pth`, `4_0_0_80x80_MiniFASNetV1SE.pth`) | **NO STATED LICENCE** | same repo, resources/anti_spoof_models | borrowed | **UNVERIFIED**: the repo states no licence for the weights. Must be resolved (pre-converted alternative with a stated licence, or a written permission) before any public release |
| JTransforms (planned for FFT; we use our own FFT now) | BSD-2-Clause | github.com/wendykierp/JTransforms | PLANNED | VERIFIED: BSD-2 header read |
| EJML (planned for matrix work) | Apache-2.0 | github.com/lessthanoptimal/ejml | PLANNED | VERIFIED: "released under the Apache v2.0 open source license" |
| jqwik (planned property tests) | EPL-2.0 | github.com/jqwik-team/jqwik | PLANNED | VERIFIED: EPL-2.0 listed on the repo page |
| MOSIP MockMDS (test fixture only, run locally, not shipped) | MPL-2.0 | github.com/mosip/mosip-mock-services | borrowed (test) | MPL-2.0 per the repo's licence; not copied into this repo |
| MOSIP android-camera-mds (MockAndroidSBI, build only, not shipped) | MPL-2.0 | github.com/mosip/android-camera-mds | borrowed (build only) | per the repo's licence; not copied into this repo |
| Flutter SDK 3.10.4 and the Dart packages of the Android client (build only) | BSD-3 (Flutter) / per-package | flutter.dev, pub.dev | borrowed (build) | **UNVERIFIED**: packages of the Android client not yet audited |
| OpenJFX (planned for the Bench, #47) | GPLv2 + Classpath Exception | github.com/openjdk/jfx | PLANNED | VERIFIED: header read |
| jai-imageio-jpeg2000 (already in the MOSIP desktop client, not ours) | BSD-3 + JJ2000 | github.com/jai-imageio/jai-imageio-jpeg2000 | not ours | VERIFIED: README (JJ2000 is not GPL-compatible) |
| ffmpeg (eval tooling and test-clip generation) | LGPL/GPL depending on build | ffmpeg.org | borrowed (tool) | **UNVERIFIED** build configuration; used as an external tool, not linked |
| scikit-image (fixture source only, Python venv in tools-local) | BSD-3 | scikit-image.org | borrowed (tool) | used to produce the public-domain NASA photo fixture |

## Data
- `testdata/fixtures-faces/one_face.jpg`: scikit-image `astronaut()`, a NASA photo (public domain). Other fixtures are generated from it (see that folder's README).
- `testdata/clips-synthetic/`: ffmpeg test pattern, not a person.
- `testdata/reference/minifas_pytorch_ref.json`: outputs of our own PyTorch run.
- No real person's recording is in the repo. Recordings live outside git (`eval/clips/`, git-ignored).

## Not in this repo (on purpose)
- Model files (`*.onnx`, `*.pth`, `*.task`, `*.tflite`) live in `tools-local/` outside git until the licence checks above are closed.
