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

| 0004-mockmds-replay-fps-mockflag.patch | mosip-mock-services `master` @ the commit in `upstream/` (MockMDS module), `a/MockMDS/src` paths | #37 | Face stream replays JPEG frames from `-Dsajeev.replay.dir=<folder>` at `-Dsajeev.replay.fps=<1..60>` (deadline-scheduled); discovery responses carry `"mock": true`. Off by default: without the properties the stream is the upstream behaviour. |

Run the patched MockMDS (from a Maven-packaged build copy): `java -Dsajeev.replay.dir=<folder> -Dsajeev.replay.fps=15 -cp "target/classes:<classpath>" io.mosip.mock.sbi.test.TestMockSBI "mosip.mock.sbi.device.purpose=Registration" "mosip.mock.sbi.biometric.type=Biometric Device"`. Measure with `python3 eval/rig/check_mockmds_fps.py`. A webcam source is possible by writing JPEGs into the replay folder with ffmpeg; it is not scripted yet.


## Publishing to the forks (Arush's step; nothing is pushed by us)
1. On GitHub, press **Fork** on each of: `mosip/registration-client`, `mosip/android-registration-client`, `mosip/android-camera-mds`, `mosip/mosip-mock-services` (four forks under your account).
2. On the Mac: `integrations/make_fork_branches.sh ~/sajeev-forks`. It makes a fresh clone of each repo at the pinned commit, applies our patches as separate commits and creates the branches `sajeev/release-1.2.0.x`, `sajeev/liveness`, `sajeev/frame-feed` and `sajeev/replay-fps`. It was tested on 24 Oct 2026: every patch applies cleanly on a fresh clone.
3. In each folder: `git remote add fork https://github.com/<your-user>/<repo>.git` then `git push fork <branch>`.
4. In each fork, open a pull request from the branch into the fork's own default branch (the plan: PRs inside your own forks, not into MOSIP's repos).
The desktop branch targets `release-1.2.0.x` (JDK 11); the Android branch needs `android/app/libs/sajeev-engine.aar` (built from `integrations/engine-android`).

## Building the forks on a clean machine (the engine is a separate build)
1. Engine for the desktop client: in this repository run `mvn -pl liveness-api,liveness-core -DskipTests install` (JDK 17 or 11, it compiles to Java 11). That puts `io.sajeev:liveness-api` and `io.sajeev:liveness-core` in your local Maven repository, which is where `registration-services/pom.xml` looks.
2. Desktop client: `cd registration && mvn -Dgpg.skip=true install` with JDK 11 (the models are found through `-Dmosip.registration.liveness.model.dir=<models>`, see `liveness-models/README.md`).
3. Engine for the Android client: `mvn -pl liveness-api,liveness-core -DskipTests package`, then `integrations/engine-android/prepare.sh` and `gradle :engine:assembleRelease` in `integrations/engine-android`; copy `engine/build/outputs/aar/engine-release.aar` to `android/app/libs/sajeev-engine.aar` in the Android fork (Flutter 3.10.x).
