// #24 (WS1) | Bible §6.6 | Facade: a new attempt draws a fresh sequence and starts a session. Java 11 only.
package io.sajeev.active;

import io.sajeev.api.ChallengeType;
import java.util.List;

/** Draws a new challenge sequence for every attempt (so repeated tries reveal nothing) and starts its session. */
public final class ChallengeEngine {

    private final ChallengePlanner planner;
    private final List<ChallengeType> pool;
    private final int minChallenges;
    private final long timeoutMillis;

    /**
     * @param planner the sequence planner
     * @param pool the configured pool (liveness.active.challengeTypes)
     * @param minChallenges how many to issue (liveness.active.minChallenges)
     * @param timeoutMillis per-challenge timeout (liveness.active.challengeTimeoutMs)
     */
    public ChallengeEngine(ChallengePlanner planner, List<ChallengeType> pool, int minChallenges, long timeoutMillis) {
        this.planner = planner;
        this.pool = pool;
        this.minChallenges = minChallenges;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Start a new attempt.
     * @param measuredFps the stream's measured fps
     * @return a session with a freshly drawn sequence, or null when the pool is too small at this frame rate
     */
    public ChallengeSession newAttempt(double measuredFps) {
        List<ChallengeType> seq = planner.draw(pool, measuredFps, minChallenges);
        return seq.isEmpty() ? null : new ChallengeSession(seq, timeoutMillis);
    }
}
