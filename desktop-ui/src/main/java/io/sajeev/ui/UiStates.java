// #41 (WS2) | docs/UIUX | The 14 desktop screens (UX1-UX14) and the mapping from every ErrorState to a screen. Java 17 here.
package io.sajeev.ui;

import io.sajeev.api.ErrorState;
import io.sajeev.guidance.GuidanceKeys;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Builds {@link UiState}s. Messages come from the guidance keys (EN, FR, AR). */
public final class UiStates {

    private UiStates() {
    }

    private static UiState s(String title, String badge, UiState.Style st, boolean guide, String icon, int done,
                             int total, String msg, List<String> hints, List<String> buttons, String footer) {
        return new UiState(title, badge, st, guide, icon, done, total, msg, hints, buttons, footer);
    }

    private static final List<String> NONE = Collections.emptyList();

    /** UX1: resident capture, passive check running. */
    public static UiState ux1() {
        return s("ui.title.resident", "ui.badge.checking", UiState.Style.PRIMARY, true, "", 0, 0,
                "passive.checking", Arrays.asList("hint.capture_area"), NONE, "");
    }

    /** UX2: operator authentication, passive check running. */
    public static UiState ux2() {
        return s("ui.title.operator", "ui.badge.checking", UiState.Style.PRIMARY, true, "", 0, 0,
                "passive.checking", NONE, NONE, "");
    }

    /** UX3: supervisor authentication, passive check running. */
    public static UiState ux3() {
        return s("ui.title.supervisor", "ui.badge.checking", UiState.Style.PRIMARY, true, "", 0, 0,
                "passive.checking", NONE, NONE, "");
    }

    /** UX4: live preview, no face found yet. */
    public static UiState ux4() {
        return s("ui.title.resident", "ui.badge.checking", UiState.Style.PRIMARY, true, "", 0, 0,
                "error.face_not_detected", NONE, NONE, "");
    }

    /** UX5: status line while the verdict is pending. */
    public static UiState ux5() {
        return s("ui.title.resident", "ui.badge.checking", UiState.Style.PRIMARY, true, "", 0, 0,
                "passive.checking", NONE, NONE, "");
    }

    /** UX6: active challenge prompt. */
    public static UiState ux6() {
        return s("ui.title.resident", "ui.badge.challenge", UiState.Style.WARN, true, "←", 0, 3,
                "challenge.turn_left", NONE, NONE, "");
    }

    /** UX7: facial action guidance with icon. */
    public static UiState ux7() {
        return s("ui.title.resident", "ui.badge.challenge", UiState.Style.WARN, true, "◎", 0, 3,
                "challenge.blink", NONE, NONE, "");
    }

    /** UX8: challenge progress, two of three done. */
    public static UiState ux8() {
        return s("ui.title.resident", "ui.badge.challenge", UiState.Style.WARN, true, "☺", 2, 3,
                "challenge.smile", Arrays.asList("feedback.continue"), NONE, "");
    }

    /** UX9: liveness failure with hints. */
    public static UiState ux9() {
        return s("ui.title.resident", "ui.badge.not_verified", UiState.Style.BAD, false, "", 0, 0,
                "failure.liveness",
                Arrays.asList("hint.look_camera", "hint.face_visible", "hint.lighting", "hint.capture_area", "hint.follow_prompt"),
                Arrays.asList("ui.try_again"), "");
    }

    /** UX10: generic PAD failure message, no detail. */
    public static UiState ux10() {
        return s("ui.title.resident", "ui.badge.not_verified", UiState.Style.BAD, false, "", 0, 0,
                "failure.pad", NONE, Arrays.asList("ui.try_again"), "");
    }

    /** UX11: retry available. */
    public static UiState ux11() {
        return s("ui.title.resident", "ui.badge.retry", UiState.Style.WARN, false, "", 0, 0,
                "failure.liveness", NONE, Arrays.asList("ui.try_again"), "ui.attempt");
    }

    /** UX12: maximum retries reached. */
    public static UiState ux12() {
        return s("ui.title.resident", "ui.badge.stopped", UiState.Style.BAD, false, "", 0, 0,
                "failure.max_retries", NONE, NONE, "");
    }

    /** UX13: device error. */
    public static UiState ux13() {
        return s("ui.title.resident", "ui.badge.device_error", UiState.Style.BAD, false, "", 0, 0,
                "error.device_disconnected", NONE, Arrays.asList("ui.try_again", "ui.rescan"), "");
    }

    /** UX14: offline indicator on a normal capture screen. */
    public static UiState ux14() {
        return s("ui.title.resident", "ui.badge.checking", UiState.Style.PRIMARY, true, "", 0, 0,
                "passive.checking", NONE, NONE, "ui.offline");
    }

    /** @return the UX1..UX14 states in order */
    public static List<UiState> allUx() {
        return Arrays.asList(ux1(), ux2(), ux3(), ux4(), ux5(), ux6(), ux7(), ux8(), ux9(), ux10(), ux11(), ux12(), ux13(), ux14());
    }

    /**
     * The screen for an error state. Every ErrorState has one, and its message is never blank.
     * @param e a PDF error state
     * @return the matching screen
     */
    public static UiState forError(ErrorState e) {
        String key = GuidanceKeys.keyFor(e);
        boolean device = e == ErrorState.DEVICE_UNAVAILABLE || e == ErrorState.DEVICE_DISCONNECTED
                || e == ErrorState.CAMERA_UNAVAILABLE || e == ErrorState.INVALID_FRAME_DATA;
        boolean stop = e == ErrorState.MAX_RETRIES || e == ErrorState.ENGINE_ERROR;
        boolean live = e == ErrorState.FACE_NOT_DETECTED || e == ErrorState.MULTIPLE_FACES || e == ErrorState.POOR_QUALITY
                || e == ErrorState.ACTIVE_REQUIRED;
        String badge = device ? "ui.badge.device_error" : stop ? "ui.badge.stopped" : live ? "ui.badge.checking" : "ui.badge.not_verified";
        UiState.Style style = live ? UiState.Style.PRIMARY : UiState.Style.BAD;
        List<String> buttons = device ? Arrays.asList("ui.try_again", "ui.rescan")
                : stop || live ? NONE : Arrays.asList("ui.try_again");
        return s("ui.title.resident", badge, style, live, "", 0, 0, key, NONE, buttons, "");
    }
}
