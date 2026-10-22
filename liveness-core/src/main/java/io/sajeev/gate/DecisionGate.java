// #23 (WS1) | Bible §6.5 | Passive score and the Decision Gate G1–G5 (precedence order is part of the spec). Java 11 only.
// Deterministic: no model here. The gate is a table in code; no single voter can pass a face alone.
package io.sajeev.gate;

import io.sajeev.api.Vote;
import java.util.List;

/** Turns the voters' votes into one outcome. Never returns PASSED when there is any doubt. */
public final class DecisionGate {

    /** Outcomes of the gate (G1–G5). */
    public enum Outcome {
        /** G3: passive pass. The workflow may proceed. */
        PASSIVE_PASS,
        /** G2: presentation attack detected. Capture or authentication is refused. */
        PAD_FAIL,
        /** G4: the client must start an active challenge (strict when exactly one voter says ATTACK). */
        ACTIVE_REQUIRED,
        /** G5: passive is not enough and active liveness is disabled in config. Never accepted. */
        LIVENESS_NOT_ESTABLISHED,
        /** G1: stream or face not usable. Retry with guidance. */
        CANNOT_JUDGE
    }

    /** The gate's answer with its score and whether the active stage is strict. */
    public static final class Decision {
        private final Outcome outcome;
        private final double passiveScore;
        private final boolean strict;

        Decision(Outcome outcome, double passiveScore, boolean strict) {
            this.outcome = outcome;
            this.passiveScore = passiveScore;
            this.strict = strict;
        }

        /** @return the outcome */
        public Outcome outcome() {
            return outcome;
        }

        /** @return S_passive over the non-ABSTAIN voters, or 0 when none voted */
        public double passiveScore() {
            return passiveScore;
        }

        /** @return true when exactly one voter said ATTACK: that voter must clear during the challenge */
        public boolean strict() {
            return strict;
        }
    }

    private DecisionGate() {
    }

    /**
     * @param votes one vote per voter (ABSTAIN included); a voter not built yet is simply absent
     * @param weights weights aligned with votes (config), all positive
     * @param streamUsable false when the stream or face is not usable (G1)
     * @param threshold passive threshold (config, 0.80 TARGET)
     * @param minLiveVotes LIVE votes needed for a passive pass (config, 3 TARGET)
     * @param activeEnabled whether active liveness is enabled (config)
     * @return the decision
     */
    public static Decision decide(List<Vote> votes, double[] weights, boolean streamUsable, double threshold,
                                  int minLiveVotes, boolean activeEnabled) {
        return decide(votes, weights, streamUsable, threshold, minLiveVotes, activeEnabled, true);
    }

    /**
     * Same as {@link #decide(List, double[], boolean, double, int, boolean)} with the abstain rule exposed so the
     * mutation harness (#31) can switch it off. Production code always passes true: ABSTAIN never counts as LIVE.
     */
    public static Decision decide(List<Vote> votes, double[] weights, boolean streamUsable, double threshold,
                                  int minLiveVotes, boolean activeEnabled, boolean abstainNeverCountsAsLive) {
        if (votes.size() != weights.length) {
            throw new IllegalArgumentException("one weight per vote");
        }
        if (!streamUsable) {
            return new Decision(Outcome.CANNOT_JUDGE, 0.0, false);
        }
        int attacks = 0;
        int lives = 0;
        double num = 0.0;
        double den = 0.0;
        double fillNum = 0.0; // score with every ABSTAIN counted at 0.5 (neutral), see I2
        double fillDen = 0.0;
        for (int i = 0; i < votes.size(); i++) {
            Vote v = votes.get(i);
            if (!abstainNeverCountsAsLive && v.label() == Vote.Label.ABSTAIN) {
                v = new Vote(Vote.Label.LIVE, 1.0, "mutation: abstain counted as live"); // mutation only
            }
            switch (v.label()) {
                case ATTACK:
                    attacks++;
                    break;
                case LIVE:
                    lives++;
                    break;
                default:
                    break; // ABSTAIN never counts as LIVE and is left out of the score
            }
            if (v.label() != Vote.Label.ABSTAIN) {
                num += weights[i] * v.probability();
                den += weights[i];
                fillNum += weights[i] * v.probability();
            } else {
                fillNum += weights[i] * 0.5;
            }
            fillDen += weights[i];
        }
        // All voters abstained: no score, so no pass (never a division by zero that passes)
        double score = den > 0 ? num / den : 0.0;
        if (attacks >= 2) {
            return new Decision(Outcome.PAD_FAIL, score, false); // G2
        }
        // I2 (Less-Evidence-Never-Helps): a voter that abstains must not raise the score. So the pass also needs the
        // score with each abstainer counted at a neutral 0.5 (never above what a LIVE vote gives) to reach the threshold.
        boolean neutralOk = fillDen > 0 && fillNum / fillDen >= threshold;
        if (den > 0 && score >= threshold && neutralOk && attacks == 0 && lives >= minLiveVotes) {
            return new Decision(Outcome.PASSIVE_PASS, score, false); // G3
        }
        boolean strict = attacks == 1; // G4, strict when exactly one ATTACK
        if (!activeEnabled) {
            return new Decision(Outcome.LIVENESS_NOT_ESTABLISHED, score, strict); // G5
        }
        return new Decision(Outcome.ACTIVE_REQUIRED, score, strict); // G4
    }
}
