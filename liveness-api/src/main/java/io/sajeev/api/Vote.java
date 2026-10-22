// #13 (WS1) | Bible §6, §9 | Java 11 only. Frozen API: changes need a BUILD_LOG.md note.
package io.sajeev.api;

/** One inspector's opinion on a window of frames (Bible §6.4). Immutable. */
public final class Vote {
    /** The three possible opinions. ABSTAIN means "I cannot judge"; it never counts as LIVE. */
    public enum Label { LIVE, ATTACK, ABSTAIN }

    private final Label label;
    private final double probability;
    private final String reason;

    /**
     * @param label LIVE, ATTACK or ABSTAIN
     * @param probability confidence that the face is live, in [0, 1]; ignored for ABSTAIN
     * @param reason short machine-readable reason, for audit
     */
    public Vote(Label label, double probability, String reason) {
        if (label == null) {
            throw new IllegalArgumentException("label must not be null");
        }
        if (label != Label.ABSTAIN && (probability < 0.0 || probability > 1.0)) {
            throw new IllegalArgumentException("probability must be in [0, 1]");
        }
        this.label = label;
        this.probability = probability;
        this.reason = reason;
    }

    /** @return the label */
    public Label label() {
        return label;
    }

    /** @return live probability in [0, 1] */
    public double probability() {
        return probability;
    }

    /** @return the reason, for audit */
    public String reason() {
        return reason;
    }
}
