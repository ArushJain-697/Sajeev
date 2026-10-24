# liveness-models

The engine runs four pretrained models. They are **not in git**. `scripts/fetch_models.sh` downloads them (about 40 MB) into `liveness-models/models/` and checks them twice: the zip's SHA-256, and then the engine itself, which refuses any model whose hash does not match the signed manifest (`manifest.txt`, `manifest.sig`, ECDSA P-256).

```
scripts/fetch_models.sh
mvn test -Dsajeev.models=liveness-models/models
```

| Model | Used for | Source and licence |
|---|---|---|
| YuNet (`face_detection_yunet_2023mar.onnx`) | face detection | OpenCV Zoo, MIT |
| SFace (`face_recognition_sface_2021dec.onnx`) | Binding (same face live and stored) | OpenCV Zoo, Apache-2.0 |
| MediaPipe face landmarker, converted to ONNX | landmarks, iris, head pose | Google MediaPipe, Apache-2.0 per the mirrors we used (the official model card was not readable; see `../DISCLOSURES.md`) |
| MiniFASNetV2 and MiniFASNetV1SE, converted to ONNX | texture voter V1 | Minivision Silent-Face-Anti-Spoofing. The code is Apache-2.0; **the weights have no stated licence** |

The signing key in this repository is a **development key**. A real deployment would sign with MOSIP's own key.

Details of each model, its conversion and its check against the original: `LICENSES.md` and `../BUILD_LOG.md` (#03, #04, #16, #18, #27).
