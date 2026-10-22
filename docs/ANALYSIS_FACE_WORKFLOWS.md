# Face workflows in the clients, and where liveness goes (#66, ET2)

Source: the same commits as `ANALYSIS_DEVICE_INTEGRATION.md` (desktop `release-1.2.0.x` @ c1cc4e7; Android `develop` @ 6e1943c). Line numbers are from those commits. **Confirmed on 23 Oct 2026: neither client contains any liveness code** (a search for "liveness" in the desktop client returns nothing).

## Desktop (registration-client, Java / JavaFX)

### Resident (new registration) capture
- Screen: `GenericBiometricsController` (`registration-client/.../controller/device/GenericBiometricsController.java`, class at line 83). It shows the live stream and captures the face.
- Flow: stream opened (lines 483–492), `rCapture` task started, `Streamer.startStream` shows frames, and the captured face is kept as the biometric.
- **Liveness hook point:** after the stream is open and the first frames are shown, before the capture is accepted (line 492 onwards). Today nothing checks liveness here.

### Operator authentication (login and post-registration)
- Login screen: `LoginController` (`controller/auth/LoginController.java`, class at line 88) takes a user id and password (fields at lines 119–122). Password only.
- Post-registration authentication: `AuthenticationController` (`controller/auth/AuthenticationController.java`, class at line 68). Gets an OTP path (line 251) and biometric path through `GenericBiometricsController`. Reviewer flag at line 195.
- **Liveness hook point:** the biometric match step, before the match result is accepted. Today the match is accepted without a liveness check.

### Supervisor / end-of-day approval
- `EODAuthenticationController` (`controller/eodapproval/EODAuthenticationController.java`, class at line 55). Same pattern as the operator path.
- **Liveness hook point:** same as operator, before approval.

## Android (android-registration-client, Flutter + Java)
- Login: `login_page.dart` (password only today). Post-registration: `authentication_page.dart`. Supervisor approval: `approve_packet/widget/authenticate_dialogbox.dart`. All three are password-only today (noted in the Bible F5).
- Resident capture goes through an intent to the SBI app (`BiometricsDetailsApi` and the Flutter capture screens). The client gets the result back, not the frames.
- **Liveness hook point:** before the intent result is accepted (resident), and before the face match (operator, supervisor). Because the SBI app keeps the preview, the frame feed needs the stream extension (#50, #51). Without it, the Android check runs in LIMITED mode (capture-only, labelled).

## Summary table in words
- Resident: one check after the stream opens, before the capture is accepted.
- Operator and supervisor: one check before the face match is accepted, and the password path stays as fallback (config, #55, #56).
- Desktop: all three use the same stream and capture code, so one integration point serves all three once the engine is in the fork (#42–#44).

## What is NOT decided here
- The exact class that will call the engine (`LivenessFacade`, #40, is the entry point; the wiring is #42–#44).
- The threshold values (TARGET, DEV tuning in #62).
