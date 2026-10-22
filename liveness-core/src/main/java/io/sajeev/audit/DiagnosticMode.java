// #30 (WS1) | Bible §6.9, red-team R3-5 | The diagnostic (X-Ray) panel: off by default, operator only, never in packets. Java 11 only.
package io.sajeev.audit;

import java.util.Collections;
import java.util.Map;

/** Gatekeeper for diagnostic data. It tells the engine what may be shown and to whom. */
public final class DiagnosticMode {

    /** Who is looking at the screen. */
    public enum Audience { RESIDENT, OPERATOR, SUPERVISOR }

    /** Text of the persistent banner shown whenever diagnostic mode is on. */
    public static final String BANNER = "DIAGNOSTIC MODE";

    private final boolean adminEnabled;

    /** @param adminEnabled liveness.diagnostic.enabled from the admin configuration (false by default) */
    public DiagnosticMode(boolean adminEnabled) {
        this.adminEnabled = adminEnabled;
    }

    /** @return true only when the admin enabled it AND the viewer is the operator (never the resident) */
    public boolean visibleTo(Audience a) {
        return adminEnabled && a == Audience.OPERATOR;
    }

    /** @return the banner when diagnostic mode is on, else an empty string */
    public String banner() {
        return adminEnabled ? BANNER : "";
    }

    /**
     * @param a the viewer
     * @param data voter votes, score bands, fps and similar diagnostic values
     * @return the data for that viewer: empty unless the panel is visible to them
     */
    public Map<String, String> panelFor(Audience a, Map<String, String> data) {
        return visibleTo(a) ? data : Collections.emptyMap();
    }

    /** @param data diagnostic values @return always empty: diagnostics are never stored in packets (R3-5) */
    public Map<String, String> forPacket(Map<String, String> data) {
        return Collections.emptyMap();
    }
}
