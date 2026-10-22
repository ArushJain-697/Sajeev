// #30 (WS1) | Scores are biometric-derived: the log carries coarse bands, never a raw number per person. Java 11 only.
package io.sajeev.audit;

/** A coarse band for a probability or score in [0, 1]. */
public enum ScoreBand {
    LOW, MID, HIGH;

    /** @param score a value in [0, 1] @return LOW below 0.5, MID below 0.8, otherwise HIGH */
    public static ScoreBand of(double score) {
        return score < 0.5 ? LOW : score < 0.8 ? MID : HIGH;
    }
}
