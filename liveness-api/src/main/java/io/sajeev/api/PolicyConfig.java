// #13 (WS1) | Bible §6, §9 | Java 11 only. Frozen API: changes need a BUILD_LOG.md note.
package io.sajeev.api;

/** Liveness policy for one workflow (Bible §6.8, §8). Values come from configuration, never hard-coded (P-H). Immutable. */
public final class PolicyConfig {
    private final double passiveThreshold;
    private final int minChallenges;
    private final int maxRetries;

    /**
     * @param passiveThreshold passive score needed to pass without a challenge, in [0, 1]
     * @param minChallenges least challenges the client must run when a challenge is needed
     * @param maxRetries retries before the repeated-failure behaviour applies
     */
    public PolicyConfig(double passiveThreshold, int minChallenges, int maxRetries) {
        this.passiveThreshold = passiveThreshold;
        this.minChallenges = minChallenges;
        this.maxRetries = maxRetries;
    }

    /** @return passive threshold */
    public double passiveThreshold() {
        return passiveThreshold;
    }

    /** @return minimum number of challenges */
    public int minChallenges() {
        return minChallenges;
    }

    /** @return maximum retries */
    public int maxRetries() {
        return maxRetries;
    }
}
