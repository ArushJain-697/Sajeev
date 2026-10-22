// #26 (WS1) | Bible §6.6, PDF M4 | Keeps the PAD voters running during an active challenge. Java 11 only.
// The frame window spans the prompt boundary and is never reset when a challenge starts. Classical voters run on every frame.
package io.sajeev.active;

import io.sajeev.api.Frame;
import io.sajeev.api.PadVoter;
import io.sajeev.api.Vote;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Re-runs the voters on challenge frames and reports an attack as soon as the gate's G2 condition appears. */
public final class ChallengeReevaluator {

    /** What the re-evaluation says after a frame. */
    public enum Status { CONTINUE, PAD_FAIL }

    /** The final reading when the challenge sequence is finished. */
    public enum Finish { OK, STRICT_NOT_CLEARED, PAD_FAIL }

    private final List<PadVoter> voters;
    private final Set<String> cnnVoters;
    private final int windowSize;
    private final Deque<Frame> window = new ArrayDeque<>();
    private final String strictVoter;
    private boolean strictVoterCleared;
    private boolean padFail;

    /**
     * @param voters every voter that ran before the challenge (none is dropped when the challenge starts)
     * @param cnnVoters names of the voters that may be skipped under load (the texture CNN); the others never are
     * @param windowSize how many recent frames the voters see
     * @param strictVoter the one voter that said ATTACK before the challenge, or null when the gate was not strict
     */
    public ChallengeReevaluator(List<PadVoter> voters, Set<String> cnnVoters, int windowSize, String strictVoter) {
        this.voters = voters;
        this.cnnVoters = cnnVoters;
        this.windowSize = windowSize;
        this.strictVoter = strictVoter;
    }

    /**
     * Run the voters on a new frame. The window keeps the frames from before the prompt.
     * @param frame the new frame
     * @param runCnnThisFrame false when load shedding skips the CNN for this frame; classical voters still run
     * @return PAD_FAIL as soon as two voters say ATTACK on the same frame, or the strict voter says ATTACK again
     */
    public Status onFrame(Frame frame, boolean runCnnThisFrame) {
        window.addLast(frame);
        while (window.size() > windowSize) {
            window.removeFirst();
        }
        List<Frame> snapshot = new ArrayList<>(window);
        int attacks = 0;
        for (PadVoter v : voters) {
            if (!runCnnThisFrame && cnnVoters.contains(v.name())) {
                continue; // the only thing load shedding may skip
            }
            Vote vote = v.vote(snapshot);
            if (vote.label() == Vote.Label.ATTACK) {
                attacks++;
                if (v.name().equals(strictVoter)) {
                    padFail = true;
                }
            } else if (vote.label() == Vote.Label.LIVE && v.name().equals(strictVoter)) {
                strictVoterCleared = true;
            }
        }
        if (attacks >= 2) {
            padFail = true;
        }
        return padFail ? Status.PAD_FAIL : Status.CONTINUE;
    }

    /**
     * Combine with the challenge session's end.
     * @return PAD_FAIL if an attack was seen during the challenge; STRICT_NOT_CLEARED when the gate was strict and the
     *         flagged voter never voted LIVE; otherwise OK
     */
    public Finish finish() {
        if (padFail) {
            return Finish.PAD_FAIL;
        }
        if (strictVoter != null && !strictVoterCleared) {
            return Finish.STRICT_NOT_CLEARED;
        }
        return Finish.OK;
    }

    /** @return the names of the voters that ran on the last frame's window, for tests and audit */
    Set<String> voterNames() {
        Set<String> s = new HashSet<>();
        for (PadVoter v : voters) {
            s.add(v.name());
        }
        return s;
    }

    /** @return the current window length (frames kept across the prompt boundary) */
    int windowLength() {
        return window.size();
    }
}
