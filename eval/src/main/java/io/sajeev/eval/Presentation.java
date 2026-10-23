// #60 (WS4) | One presentation (one ~10 s clip): who, what, and what the engine decided. Java 11 only.
package io.sajeev.eval;

import java.util.Map;
import java.util.TreeMap;

/** Immutable result of running one clip. Votes are by voter name (LIVE, ATTACK, ABSTAIN) with probabilities. */
public final class Presentation {
    public final String file;
    public final String species; // "bona" or P1..P6
    public final String subject;
    public final String device;
    public final boolean accepted; // the engine's final answer: a live verdict
    public final String tier;
    public final Map<String, String> voteLabels;
    public final Map<String, Double> voteProbabilities;
    public final boolean strictGate;

    public Presentation(String file, String species, String subject, String device, boolean accepted, String tier,
                        Map<String, String> voteLabels, Map<String, Double> voteProbabilities, boolean strictGate) {
        this.file = file;
        this.species = species;
        this.subject = subject;
        this.device = device;
        this.accepted = accepted;
        this.tier = tier;
        this.voteLabels = new TreeMap<>(voteLabels);
        this.voteProbabilities = new TreeMap<>(voteProbabilities);
        this.strictGate = strictGate;
    }

    public boolean isBonaFide() {
        return "bona".equals(species);
    }
}
