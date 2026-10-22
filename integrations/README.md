# Integrations: client changes as patches (until the forks exist)

The desktop client changes are kept here as patch files against the pinned upstream commit. When the GitHub forks exist (D26), each patch is applied on its fork branch and becomes a pull request inside the fork.

| Patch | Against | Ticket | Notes |
|---|---|---|---|
| 0001-registration-api-liveness-spi.patch | registration-client `release-1.2.0.x` @ c1cc4e7fc1625ccc6ef26282910934bfcb9d164c | #40 | Adds the liveness SPI (`LivenessFacade`, `LivenessService`, `LivenessResult`, `LivenessOutcome`) and a fail-closed stub. Adds JUnit test scope to `registration-api-stub-impl/pom.xml`. |

Apply with: `git apply integrations/patches/<file>` inside a checkout at the pinned commit.

Not yet pinned: the `develop` line (failed, #05), `android-registration-client` (pin pending the Flutter decision D44 and the fork), `mosip-mock-services` and `android-camera-mds` (used locally as build copies, not forked yet).
| 0002-sbitestclient-for-active-mock.patch | android-camera-mds `develop` @ da9d7e49edae2a94cccddf948b7e7633360cd837, module SBITestClient | #09 | Makes the repo's test client talk to the ACTIVE mock (`io.mosip.mock.sbi`): action `io.sbi.device`, targetSdk 29 (Android 11 package visibility), result key `response` (byte array), `.Info` / `.rCapture` action case, package compare against `io.mosip.mock.sbi`. Test tooling only; not shipped. |

## Driving the Android mock on the emulator (#09)
1. Build `MockAndroidSBI` (Gradle 8.5, JDK 17) and the patched `SBITestClient` (Gradle 6.7.1, JDK 11); install both.
2. Give the mock a device keystore: copy MockMDS's `mosipface.p12` into the mock's `files/device_p12_file.p12` and write `eval/rig/mock_sbi_prefs.xml` to its `shared_prefs/io.mosip.mock.sbi_preferences.xml` (alias and password are both `mosipface`), using `adb shell run-as io.mosip.mock.sbi`. Without this the mock cannot sign Info or rCapture.
3. Run `python3 eval/rig/drive_sbi_test_client.py` (taps DISCOVER, INFO, RCAPTURE and waits on the mock's log markers).
4. The capture response lands in the mock's `files/output/<client>/resp_*.txt` (read with `run-as`). A copy is in `testdata/recorded-android/rcapture-face.json`.

| 0003-mockandroidsbi-frame-feed.patch | android-camera-mds `develop` @ da9d7e49edae2a94cccddf948b7e7633360cd837, module MockAndroidSBI | #50 | Adds `io.mosip.mock.sbi.feed.FrameFeedService` (bound service, Messenger), `ReplaySource` (MOCK frames), signature-level permission `io.mosip.mock.sbi.permission.FRAME_FEED`, and `mock:true` in `DeviceInfoResponse`. The patch also records a file-mode change on `gradlew` (made executable in the build copy); ignore it. |

`integrations/feedprobe/` is a tiny test app (`io.sajeev.feedprobe`) that binds to the feed and logs what it receives. Test tooling only. Run `eval/rig/test_feed.sh`.

