// #26 (WS1) | Tests named as claims. Voters are scripted fakes: the re-evaluation logic is tested on its own.
package io.sajeev.active;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sajeev.api.Frame;
import io.sajeev.api.PadVoter;
import io.sajeev.api.Vote;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

class ChallengeReevaluatorTest {

    /** A voter whose answer depends on the frame sequence of the newest frame in its window. */
    private static final class FakeVoter implements PadVoter {
        private final String name;
        private final IntFunction<Vote.Label> script;
        final List<Integer> windowSizes = new ArrayList<>();
        int calls;

        FakeVoter(String name, IntFunction<Vote.Label> script) {
            this.name = name;
            this.script = script;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Vote vote(List<Frame> window) {
            calls++;
            windowSizes.add(window.size());
            Vote.Label l = script.apply((int) window.get(window.size() - 1).sequence());
            return new Vote(l, l == Vote.Label.LIVE ? 0.95 : 0.05, "t");
        }
    }

    private static Frame frame(long seq) {
        return new Frame(new byte[] {1}, seq * 33_000_000L, seq);
    }

    @Test
    void test_attack_vote_during_challenge_fails_the_session() {
        FakeVoter a = new FakeVoter("V2", s -> s >= 5 ? Vote.Label.ATTACK : Vote.Label.LIVE);
        FakeVoter b = new FakeVoter("V4", s -> s >= 5 ? Vote.Label.ATTACK : Vote.Label.LIVE);
        FakeVoter c = new FakeVoter("V5", s -> Vote.Label.LIVE);
        ChallengeReevaluator r = new ChallengeReevaluator(Arrays.<PadVoter>asList(a, b, c), Collections.<String>emptySet(), 4, null);
        for (int i = 0; i < 5; i++) {
            assertEquals(ChallengeReevaluator.Status.CONTINUE, r.onFrame(frame(i), true));
        }
        assertEquals(ChallengeReevaluator.Status.PAD_FAIL, r.onFrame(frame(5), true));
        assertEquals(ChallengeReevaluator.Finish.PAD_FAIL, r.finish());
    }

    @Test
    void test_one_attack_alone_does_not_fail_when_not_strict() {
        FakeVoter a = new FakeVoter("V2", s -> Vote.Label.ATTACK);
        FakeVoter b = new FakeVoter("V4", s -> Vote.Label.LIVE);
        ChallengeReevaluator r = new ChallengeReevaluator(Arrays.<PadVoter>asList(a, b), Collections.<String>emptySet(), 4, null);
        for (int i = 0; i < 6; i++) {
            r.onFrame(frame(i), true);
        }
        assertEquals(ChallengeReevaluator.Finish.OK, r.finish(), "a single ATTACK vote is the gate's job (strict mode), not G2");
    }

    @Test
    void test_strict_mode_requires_flagged_voter_to_clear() {
        // V3 said ATTACK before the challenge: strict mode.
        FakeVoter cleared = new FakeVoter("V3", s -> s >= 3 ? Vote.Label.LIVE : Vote.Label.ABSTAIN);
        FakeVoter other = new FakeVoter("V4", s -> Vote.Label.LIVE);
        ChallengeReevaluator ok = new ChallengeReevaluator(Arrays.<PadVoter>asList(cleared, other), Collections.<String>emptySet(), 4, "V3");
        for (int i = 0; i < 6; i++) {
            ok.onFrame(frame(i), true);
        }
        assertEquals(ChallengeReevaluator.Finish.OK, ok.finish());

        FakeVoter silent = new FakeVoter("V3", s -> Vote.Label.ABSTAIN);
        ChallengeReevaluator notCleared = new ChallengeReevaluator(Arrays.<PadVoter>asList(silent, other), Collections.<String>emptySet(), 4, "V3");
        for (int i = 0; i < 6; i++) {
            notCleared.onFrame(frame(i), true);
        }
        assertEquals(ChallengeReevaluator.Finish.STRICT_NOT_CLEARED, notCleared.finish(), "abstaining all through is not clearing");

        FakeVoter again = new FakeVoter("V3", s -> s == 4 ? Vote.Label.ATTACK : Vote.Label.LIVE);
        ChallengeReevaluator reattack = new ChallengeReevaluator(Arrays.<PadVoter>asList(again, other), Collections.<String>emptySet(), 4, "V3");
        for (int i = 0; i < 6; i++) {
            reattack.onFrame(frame(i), true);
        }
        assertEquals(ChallengeReevaluator.Finish.PAD_FAIL, reattack.finish(), "the flagged voter attacking again fails the session");
    }

    @Test
    void test_voter_window_spans_the_prompt_boundary() {
        FakeVoter v = new FakeVoter("V2", s -> Vote.Label.LIVE);
        ChallengeReevaluator r = new ChallengeReevaluator(Arrays.<PadVoter>asList(v), Collections.<String>emptySet(), 5, null);
        for (int i = 0; i < 4; i++) {
            r.onFrame(frame(i), true); // frames before the prompt
        }
        // the prompt appears here: the window is not reset
        r.onFrame(frame(4), true);
        r.onFrame(frame(5), true);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 5), v.windowSizes, "the window grows across the boundary up to its size, never back to 1");
    }

    @Test
    void test_cnn_may_be_skipped_under_load_but_pad_voters_never_are() {
        FakeVoter cnn = new FakeVoter("V1_texture", s -> Vote.Label.LIVE);
        FakeVoter moire = new FakeVoter("V2_moire", s -> Vote.Label.LIVE);
        FakeVoter edge = new FakeVoter("V4_edge", s -> Vote.Label.LIVE);
        ChallengeReevaluator r = new ChallengeReevaluator(Arrays.<PadVoter>asList(cnn, moire, edge),
                new HashSet<>(Collections.singletonList("V1_texture")), 4, null);
        for (int i = 0; i < 10; i++) {
            r.onFrame(frame(i), i % 3 == 0); // the CNN runs on every third frame only
        }
        assertEquals(10, moire.calls);
        assertEquals(10, edge.calls);
        assertTrue(cnn.calls < 10);
    }
}
