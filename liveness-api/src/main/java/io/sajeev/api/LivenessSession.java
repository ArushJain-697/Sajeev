// #13 (WS1) | Bible §6, §9 | Java 11 only. Frozen API: changes need a BUILD_LOG.md note.
package io.sajeev.api;

/** One liveness check for one person at one workflow step. Single-use. */
public interface LivenessSession {

    /** Session states, in order. */
    enum State { PASSIVE, ACTIVE, DONE, ABORTED }

    /** @param frame the next frame from the stream */
    void onFrame(Frame frame);

    /** @return the current state */
    State state();

    /** @return the verdict once the state is DONE or ABORTED; null before that */
    Verdict verdict();

    /** Stop the session and release its resources. Safe to call more than once. */
    void abort();
}
