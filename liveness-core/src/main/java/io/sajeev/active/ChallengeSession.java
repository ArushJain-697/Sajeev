// #24 (WS1) | Bible §6.6 | One challenge sequence in progress: prompt, guard frame, timeout. Java 11 only.
package io.sajeev.active;

import io.sajeev.api.ChallengeType;
import java.util.List;

/**
 * Tracks one attempt through its challenges. Time is the engine's monotonic clock in nanoseconds and the frames'
 * arrival times; it never uses wall-clock time. Detecting an action is #25's job: this class only consumes the result.
 */
public final class ChallengeSession {

    /** Where the attempt stands. */
    public enum Outcome { IN_PROGRESS, ACTIVE_PASS, CHALLENGE_TIMEOUT }

    private final List<ChallengeType> sequence;
    private final long timeoutNanos;
    private int index;
    private long promptedAtNanos = -1;
    private long guardUntilSequence = -1;
    private Outcome outcome = Outcome.IN_PROGRESS;

    /**
     * @param sequence the drawn challenges, in order (at least one)
     * @param timeoutMillis per-challenge timeout (config, 5000 TARGET)
     */
    public ChallengeSession(List<ChallengeType> sequence, long timeoutMillis) {
        if (sequence.isEmpty()) {
            throw new IllegalArgumentException("a challenge sequence needs at least one challenge");
        }
        this.sequence = sequence;
        this.timeoutNanos = timeoutMillis * 1_000_000L;
    }

    /**
     * The prompt for the current challenge is now on screen.
     * @param nowNanos the monotonic time when the prompt appeared
     * @param lastFrameSequence the sequence of the newest frame already received. That frame, and the next one that
     *                          may already be in flight, cannot complete the challenge (guard of one frame).
     */
    public void prompted(long nowNanos, long lastFrameSequence) {
        this.promptedAtNanos = nowNanos;
        this.guardUntilSequence = lastFrameSequence + 1;
    }

    /**
     * Feed one frame's result.
     * @param frameSequence the frame's sequence number
     * @param arrivalNanos the frame's arrival time (monotonic)
     * @param actionDetected true if the current challenge's action is seen on this frame
     * @return the outcome so far
     */
    public Outcome onFrame(long frameSequence, long arrivalNanos, boolean actionDetected) {
        if (outcome != Outcome.IN_PROGRESS || promptedAtNanos < 0) {
            return outcome;
        }
        boolean afterPrompt = arrivalNanos >= promptedAtNanos && frameSequence > guardUntilSequence;
        if (actionDetected && afterPrompt) {
            index++;
            promptedAtNanos = -1; // the caller prompts the next challenge
            if (index >= sequence.size()) {
                outcome = Outcome.ACTIVE_PASS;
            }
            return outcome;
        }
        if (arrivalNanos - promptedAtNanos > timeoutNanos) {
            outcome = Outcome.CHALLENGE_TIMEOUT;
        }
        return outcome;
    }

    /** @return the challenge to prompt now, or null when the sequence is finished */
    public ChallengeType current() {
        return index < sequence.size() ? sequence.get(index) : null;
    }

    /** @return the outcome so far */
    public Outcome outcome() {
        return outcome;
    }
}
