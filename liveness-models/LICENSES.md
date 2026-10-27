# Model licences

| File | Licence | Source | Ours or borrowed |
|---|---|---|---|
| `face_detection_yunet_2023mar.onnx` | MIT | OpenCV Zoo (ShiqiYu/libfacedetection) | borrowed, not retrained |
| `face_recognition_sface_2021dec.onnx` | Apache-2.0 | OpenCV Zoo (SFace) | borrowed, not retrained |
| `face_landmarks_detector.onnx` (converted from MediaPipe `face_landmarker.task`) | Apache-2.0 per mirrors; the official model card could not be read (UNVERIFIED) | Google MediaPipe | borrowed, converted to ONNX by us |
| `2.7_80x80_MiniFASNetV2.onnx(.data)`, `4_0_0_80x80_MiniFASNetV1SE.onnx(.data)` (converted from the Minivision `.pth` weights) | **NO STATED LICENCE** for the weights (the code is Apache-2.0) | minivision-ai/Silent-Face-Anti-Spoofing | borrowed, converted to ONNX by us |

The manifest (`manifest.txt`, `manifest.sig`) is signed with a development key generated for this project.
