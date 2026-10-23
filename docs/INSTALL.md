# Install and run

State on 23 Oct 2026. Everything below was run on one Apple-silicon Mac; nothing was run on Windows or a real phone.

## Needs
- JDK 17 to build (the engine is compiled for Java 11 with `--release 11`; the desktop client patches build with JDK 11), Maven 3.9.
- Model files, kept outside git: `yunet/face_detection_yunet_2023mar.onnx`, `landmark/face_landmarks_detector.onnx`, `minifasnet/2.7_80x80_MiniFASNetV2.onnx` and `4_0_0_80x80_MiniFASNetV1SE.onnx` (each with its `.onnx.data` file), `sface/face_recognition_sface_2021dec.onnx`. Sources and licences: `DISCLOSURES.md` (the MiniFASNet weights licence is unverified).
- For the Android parts: Android SDK, Gradle 8.5, Flutter 3.10.4, an emulator (Android 13 arm64 was used). For the desktop client patches: a checkout of `registration-client` `release-1.2.0.x` at c1cc4e7.

## Signing the models
The desktop bridge and the Android app refuse models that are not covered by a signed manifest (`manifest.txt`, `manifest.sig` in the model folder). Sign a model folder with `java -cp liveness-core/target/classes io.sajeev.model.ManifestTool sign <models> <version> <private.der> <models>/manifest.txt <models>/manifest.sig`; a key pair is made with `ManifestTool genkey`. The public half is `liveness-core/src/main/resources/model-signing-public.der` (a DEV key: for a real release, MOSIP or you must hold a production key and replace this file). The private key must never be committed.

## Engine, bench, eval
```
export SAJEEV_MODELS=/path/to/models
mvn -pl liveness-api,liveness-core,devices,bench,eval test
bench/run.sh --clip testdata/fixtures-faces/one_face.jpg
eval/run_eval.sh --manifest eval/clips/<session>/manifest.csv --clips eval/clips/<session> --out metrics.json
bench/perf.sh        # regenerates docs/PERFORMANCE.md
docs/gen_test_cases.sh
```
Tests that need the models skip when `SAJEEV_MODELS` is not set. The device contract tests skip when no MockMDS answers on port 4501 (`integrations/README.md` explains how to run the patched mock).

## Desktop client
Apply `integrations/patches/0001-*` then `0005-*` with `git apply` inside the `registration` folder of a checkout at the pinned commit; `mvn -o -Dgpg.skip=true -pl registration-api,registration-api-stub-impl,registration-services,registration-client install` with JDK 11. The engine jars must be installed in the local Maven repository first (`mvn install -DskipTests` in this repo for `liveness-api` and `liveness-core`). Set `-Dmosip.registration.liveness.model.dir=<models>` for the client JVM. Server-side settings: `docs/CONFIG.md`.

## Android client
`integrations/engine-android/prepare.sh` then `gradle assembleRelease` gives `engine-release.aar`. In the Android client checkout apply `0006-*` and `0007-*`, copy the AAR to `android/app/libs/sajeev-engine.aar`, generate the Pigeon files with `pigeon.sh`, and build with `flutter build apk --debug`. Put the models into the app's `files/models` folder. The patched MockAndroidSBI (patch `0003`) provides the frame feed.

## Not covered
No installer, no packaging for MOSIP's release process, and the clients were not run against a MOSIP server.
