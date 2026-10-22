# UI/UX specification (#12)

**Status: written specification, not visual mockups.** Every one of the 15 PDF UI/UX items has a section below with its screen, its states and the message keys it uses (from `liveness-core/src/main/resources/guidance/`). Visual mockups (images or Figma) are still to be made from these sections. Nothing here claims a screenshot exists.

Both platforms use the same states, the same wording (same keys) and the same order. Desktop: JavaFX (FXML). Android: Flutter.

## 1. Resident face capture screen (PDF UX1)
- Shows: live face preview, a face-positioning guide (oval), liveness status, capture progress, and the text "Checking face liveness…" during passive checks.
- States: idle, passive (no action needed), active (challenge shown), accepted (feedback.captured), failed (see 9–12).
- Keys: passive.checking, feedback.captured, hint.capture_area.

## 2. Operator authentication screen (PDF UX2)
- Same layout as 1, with the operator's name shown. No liveness test choice (PDF 8: the system picks).
- Keys: as 1.

## 3. Supervisor authentication screen (PDF UX3)
- Same as 2, for supervisor approval and end-of-day authentication.
- Keys: as 1.

## 4. Live face preview (PDF UX4)
- Mirrored preview (the person sees a mirror image, as in a camera app). The challenge direction ("left") always means the person's own left (R1-11).
- A thin frame around the face while it is found. Hidden when no face is found.

## 5. Liveness verification status (PDF UX5)
- One line of text plus a progress mark: checking (passive), challenge (active), verified, not verified.
- Never shows the detector score or the voter names to the person (diagnostic mode only, off by default).

## 6. Active challenge prompts (PDF UX6)
- Text from the challenge.* keys, read aloud from the spoken.challenge.* keys.
- Shown only when passive liveness is below threshold (PDF 3). The person does not choose the prompt.

## 7. Facial action guidance (PDF UX7)
- Icons plus text for each action: blink, smile, turn left, turn right, look left/right/up/down, eyes closed, open mouth. The icon is shown even when the text is not read.

## 8. Challenge progress indication (PDF UX8)
- A row of dots, one per challenge in the set, filled as each is detected. Uses shape and position, not colour alone.

## 9. Liveness failure messages (PDF UX9)
- Text: failure.liveness ("We could not verify face liveness. Please try again.") plus hints (hint.look_camera, hint.face_visible, hint.lighting, hint.capture_area, hint.follow_prompt). Hints are chosen by the tracker reason.

## 10. PAD / failure handling messages (PDF UX10)
- A generic message only: failure.pad ("Face verification could not be completed. Please try again."). No attack type, no score, no hint about what was detected.
- Details go to the audit log, not the screen.

## 11. Retry flow (PDF UX11)
- The screen says a retry is available and how many are left, for example "Attempt 2 of 3". The count comes from the policy (maxRetries).
- Retry starts a new session with a new challenge set.

## 12. Maximum retry / failure flow (PDF UX12)
- At the limit: failure.max_retries, and the recovery process named by the policy (onRepeatedFailure, default BLOCK). The screen does not reveal why.
- SUPERVISOR_OVERRIDE, if configured, is an audited action with its own screen. Not shown by default.

## 13. Device error handling (PDF UX13)
- Each device error has its own text: no camera device (error.device_unavailable), camera not available (error.camera_unavailable), connection lost (error.device_disconnected), device error (error.invalid_frame).
- Offers "Try again" and "Re-scan devices". Never shows a raw SBI error code to the person (the code goes to the log).

## 14. Online/offline UI behaviour (PDF UX14)
- A small status mark: online, or offline with the last synced configuration in use. Same liveness flow in both.
- Offline never weakens the check. The screen does not explain the difference beyond the mark.

## 15. Consistent Desktop and Android experience (PDF UX15)
- Same states, same keys, same order, same timing of messages. Differences only where the platform forces them (for example the camera permission dialog on Android).

## Accessibility checklist (PDF M10 and the plan)
- Text contrast at least 4.5:1 against its background (WCAG AA for normal text). Check with a contrast tool before sign-off.
- Text scales with the system setting without cutting words off.
- Colour is never the only cue: every state also has a text label and a shape.
- Every challenge is also spoken (spoken.challenge.* keys), for people who cannot see the screen well.
- Screen-reader labels on every control (TalkBack on Android; accessible names on the JavaFX controls).
- Arabic (ar) lays out right-to-left: arrows, progress dots and the oval guide mirror correctly (R2-5).
- Touch targets at least 48 dp on Android.

## Not done here
- Visual mockups, colour values and icon artwork.
- User testing with real people (none planned before the held-out recording).
- Arabic and French wording is a machine translation, not reviewed (see guidance bundles).
