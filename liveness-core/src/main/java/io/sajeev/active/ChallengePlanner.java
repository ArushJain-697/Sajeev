// #24 (WS1) | Bible §6.6 | Draws the challenge sequence for one attempt. SecureRandom, no repeats, fps-filtered. Java 11 only.
package io.sajeev.active;

import io.sajeev.api.ChallengeType;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Chooses which challenges to issue. The client picks them, never the person. */
public final class ChallengePlanner {

    /** Lowest measured fps at which a challenge may be issued. BLINK's 8 is a starting guess, replaced by #61. */
    private final Map<ChallengeType, Double> minFps = new EnumMap<>(ChallengeType.class);
    private final SecureRandom random;

    /**
     * @param blinkMinFps measured fps below which BLINK is never issued (8 TARGET)
     * @param otherMinFps measured fps below which the other types are never issued (3, the PDF minimum)
     * @param random a SecureRandom: a plain Random would make the order predictable (PDF O12, O13)
     */
    public ChallengePlanner(double blinkMinFps, double otherMinFps, SecureRandom random) {
        if (random == null) {
            throw new IllegalArgumentException("a SecureRandom is required");
        }
        for (ChallengeType t : ChallengeType.values()) {
            minFps.put(t, t == ChallengeType.BLINK ? blinkMinFps : otherMinFps);
        }
        this.random = random;
    }

    /**
     * Draw a fresh sequence for one attempt. Call this again for every retry: the sequence is never reused.
     * @param pool the configured pool of challenge types
     * @param measuredFps the stream's measured frame rate
     * @param count how many challenges to issue (config minChallenges, at least 1)
     * @return {@code count} distinct challenge types in random order, or an empty list when no
     *         type (or only one, for a sequence longer than the pool) is allowed at this frame rate (the caller must then treat
     *         active liveness as unavailable). Up to the pool size the types are distinct; beyond it they repeat, never twice in a row.
     */
    public List<ChallengeType> draw(List<ChallengeType> pool, double measuredFps, int count) {
        List<ChallengeType> allowed = new ArrayList<>();
        for (ChallengeType t : pool) {
            if (measuredFps >= minFps.get(t) && !allowed.contains(t)) {
                allowed.add(t);
            }
        }
        if (count < 1 || allowed.isEmpty()) {
            return Collections.emptyList();
        }
        if (allowed.size() < count) {
            if (allowed.size() < 2) {
                return Collections.emptyList(); // a repeat-free-in-a-row sequence needs at least two types
            }
            // A long "follow the prompts" sequence (for example six directions out of four): the same type may come back
            // later, never twice in a row. Chance of a replay getting every prompt right in time falls with the length.
            List<ChallengeType> seq = new ArrayList<>();
            ChallengeType prev = null;
            while (seq.size() < count) {
                ChallengeType next = allowed.get(random.nextInt(allowed.size()));
                if (next != prev) {
                    seq.add(next);
                    prev = next;
                }
            }
            return seq;
        }
        Collections.shuffle(allowed, random); // Fisher-Yates with the SecureRandom
        return new ArrayList<>(allowed.subList(0, count));
    }
}
