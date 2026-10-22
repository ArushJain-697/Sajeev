# How the clients talk to biometric devices (#66, ET1)

Source: MOSIP registration-client `release-1.2.0.x` @ c1cc4e7 (the line the Collab server hands out), read on 23 Oct 2026. Paths are relative to `registration/` in that repo. Line numbers are from that commit. The Android client is covered briefly at the end.

## 1. Which SBI version is used
- Several provider classes exist, one per SBI spec: `registration-services/.../mdm/spec_0_9_2/`, `mdm/spec_0_9_5/`, `mdm/sbi/spec_1_0/`.
- The provider is chosen from the device's own `serviceVersion` by `MosipDeviceSpecificationHelper.getDeviceInfoDecoded` (line 105) and a class-name check (line 109).
- So the same client talks to 0.9.2, 0.9.5 and 1.0 devices, one adapter per spec.

## 2. The HTTP methods used (SBI custom methods)
- Discovery: `MOSIPDISC` in `spec_0_9_2/.../MosipDeviceSpecification_092_ProviderImpl.java:388` and `spec_0_9_5/.../MosipDeviceSpecification_095_ProviderImpl.java:435`.
- Face capture: `RCAPTURE` in `spec_0_9_2/...092_ProviderImpl.java:211`, `spec_0_9_5/...095_ProviderImpl.java:205`, and `sbi/spec_1_0/.../MosipDeviceSpecification_SBI_1_0_ProviderImpl.java:230`.
- Live stream: `STREAM` in `spec_0_9_2/...092_ProviderImpl.java:155`. In 0.9.5 the stream URL is built from the callback id plus a stream suffix (`spec_0_9_5/...095_ProviderImpl.java:142`).
- Device info: `MOSIPDINFO` (1.0 provider, same package).
- Our `Sbi10HttpAdapter` uses the same four methods, with the paths `/device`, `/info`, `/capture`, `/stream` that MockMDS serves (MockMDS `WebMvcConfigure.java` lines 54, 69, 83, 97).

## 3. Where the stream goes in the client
- `GenericBiometricsController.java:483-492`: the client asks `bioService.getStream(...)` for the face modality, checks the first byte is readable, then calls `rCaptureTaskService()` and `streamer.startStream(...)`.
- If the stream is null, it resets the device map and shows `STREAMING_ERROR` (lines 486–491). This is the place a liveness check would hook in (see the workflow file).
- `Streamer.java:88` (`startStream`) reads the multipart M-JPEG with `retrieveNextImage` (line 127), a byte-by-byte parser. Our `MjpegParser` (#15) does the same job with bounds checks.

## 4. The face record
- `rCapture` returns a signed token whose payload holds `bioValue`, an ISO/IEC 19794-5 face record. The MockMDS record carries a JPEG 2000 image (checked 23 Oct 2026, `FAC` magic). The client decodes it with `jai-imageio-jpeg2000` (see the disclosure file).
- Our engine cannot decode JP2 yet (#51/#54). `Sbi10HttpAdapter.capture()` refuses it on purpose.

## 5. Provider selection and configuration
- `DeviceSpecificationFactory.initializeDeviceMap(true)` (called at `GenericBiometricsController.java:487`) rebuilds the device map, so a broken stream re-discovers devices.
- Device discovery ports: the client scans the MOSIP range 4501–4600 (MockMDS `application.properties` lines 1–2 say the same).

## 6. Android client (brief; deeper analysis with #48 and #50)
- The Android client does not use HTTP. It starts the SBI app with Android intents: `io.sbi.device` for discovery and `io.mosip.mock.sbi.face.rCapture` and friends for capture (MockAndroidSBI `AndroidManifest.xml` lines 33–43).
- The SBI app returns results in an intent extra (`response`, byte array). We confirmed this on the emulator on 22 Oct 2026 (see BUILD_LOG #09).
- Preview: the SBI app keeps its own preview. Frames are not available to the client, which is why the stream extension is needed (Bible §6.10).

## 7. What this means for the design
- Our adapters (`Sbi10HttpAdapter`, `Sbi095HttpAdapter`, `AndroidSbiStreamAdapter` later) sit at the same point as these providers.
- The liveness check goes after the stream is open and before `rCapture` is accepted (see `ANALYSIS_FACE_WORKFLOWS.md`).
