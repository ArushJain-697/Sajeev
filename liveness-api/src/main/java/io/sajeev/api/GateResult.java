// #13 (WS1) | Bible §6, §9 | Java 11 only. Frozen API: changes need a BUILD_LOG.md note.
package io.sajeev.api;

/** Result of the Decision Gate after the passive stage (Bible §6.5). Immutable. */
public final class GateResult {
    /** What the gate decided. */
    public enum Decision { LIVE_PASSIVE, ACTIVE_REQUIRED, ATTACK_SUSPECTED, CANNOT_JUDGE }

    private final Decision decision;
    private final double passiveScore;
    private final String reason;

    /**
     * @param decision the gate decision
     * @param passiveScore combined passive score in [0, 1]
     * @param reason short reason, for audit
     */
    public GateResult(Decision decision, double passiveScore, String reason) {
        this.decision = decision;
        this.passiveScore = passiveScore;
        this.reason = reason;
    }

    /** @return the decision */
    public Decision decision() {
        return decision;
    }

    /** @return combined passive score */
    public double passiveScore() {
        return passiveScore;
    }

    /** @return the reason */
    public String reason() {
        return reason;
    }
}
