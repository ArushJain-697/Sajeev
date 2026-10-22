# Technology evaluation (#67, ET5)

Every claim is either backed by a source we checked (cited), or marked **UNVERIFIED**. Nothing is presented as measured unless the file says so. Decisions are reversible; the reasons are here so they can be revisited.

## 1. Candidates for the passive texture check (V1)
- **MiniFASNet (Minivision, Silent-Face-Anti-Spoofing).** Code licence Apache-2.0 (checked on GitHub, 22 Oct 2026). **Weights: no licence stated** in the repo (checked). Two models, V2 (scale 2.7) and V1SE (scale 4.0), 1.8 MB each. Converted to ONNX; ONNX matches PyTorch within 2.9e-6 (our test). **Chosen**, with the weights licence still to be confirmed (see DISCLOSURES.md).
- **Other open anti-spoof models:** **UNVERIFIED** as to licence and accuracy. Not evaluated in this run. Fallback if MiniFASNet's weights fail the licence check: a third-party pre-converted ONNX with a stated licence, or drop V1 (the gate then runs on V2–V5).
- **Commercial face-liveness SDKs:** rejected. Cost and licence terms do not fit an open OSI-licensed engine, and they would be a black box for judges (ISO/IEC 30107 evidence needs our own measurement).

## 2. Face detection and landmarks
- **YuNet (OpenCV Zoo).** MIT licence (checked: LICENSE file, 22 Oct 2026). 232 KB ONNX. Chosen. Our Java decode matches the Python reference on all fixtures.
- **MediaPipe Face Landmarker.** Code Apache-2.0 (checked). The **model card terms could not be read** (Google Drive preview, sign-in needed). Third-party mirrors state Apache-2.0 for the weights, **UNVERIFIED** against the original card. Chosen for landmarks and iris, and flagged in DISCLOSURES.md.
- **dlib 68-point landmarks:** rejected. Different indices; the blueprint warns against mixing them; not needed when MediaPipe covers iris (checked: 478 points in the converted model output).

## 3. Runtime
- **ONNX Runtime (MIT).** Java 1.19.2 jar works on JDK 11 and 21 (tests run). Chosen for desktop and Android. Accelerators (CoreML, NNAPI) **not yet measured**: P0-8 / #10 is open.
- **OpenCV:** rejected for the engine. One native library per OS; D23 chose pure Java for both clients.

## 4. Signal processing
- **Pure Java FFT** (in-house, radix-2, `liveness-core/passive/moire`). Chosen over JTransforms for now (JTransforms is BSD-2 if we switch, checked). EJML Apache-2.0 (checked) for matrices later.

## 5. Rejected approaches, with reasons
- **rPPG (heart-rate from skin colour).** Rejected: needs a stable, well-lit face for many seconds, fails on dark skin tones in the published literature (**UNVERIFIED** as a specific citation; stated as a risk, not a measured result). Also a fairness risk. Not built.
- **Depth or IR cameras.** Rejected: the PDF targets L0/L1 webcams and phones. Depth cameras are not in the MOSIP device set we can test (**UNVERIFIED** for every real device in the field).
- **Blink-only challenges.** Rejected as the sole check: the 3-fps trap (BUILD_MAP, Bible §3.2). Kept as one of ten challenges with the fps gate.

## 6. Cost and offline fit
- Everything listed above runs offline on the device. No network call in the liveness path (Bible §16). Model sizes: 1.8 MB, 1.8 MB, 0.23 MB, 3.7 MB, 4.9 MB (ONNX).
- Cost: all chosen components are free and open-licensed, with the two open licence checks above.

## 7. Decision
- Chosen stack: MiniFASNet (V1), YuNet (detector), MediaPipe face mesh + iris (ONNX), ONNX Runtime, pure-Java signal processing.
- Open before any public claim: the MiniFASNet weights licence and the MediaPipe model card. Both are recorded in DISCLOSURES.md and HONESTY_INVENTORY.md.
