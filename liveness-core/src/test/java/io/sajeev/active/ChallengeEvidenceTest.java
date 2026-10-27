// Red-team 2 fixes in the challenge re-evaluation: ATTACK votes of different voters add up over the window, and the strict
// voter must clear on at least two frames.
package io.sajeev.active;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.sajeev.api.Frame;
import io.sajeev.api.PadVoter;
import io.sajeev.api.Vote;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

class ChallengeEvidenceTest {

    private static PadVoter voter(String name, IntFunction<Vote.Label> script) {
        return new PadVoter() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Vote vote(List<Frame> window) {
                Vote.Label l = script.apply((int) window.get(window.size() - 1).sequence());
                return new Vote(l, l == Vote.Label.LIVE ? 0.95 : 0.05, "t");
            }
        };
    }

    private static Frame frame(long seq) {
        return new Frame(new byte[] {1}, seq * 33_000_000L, seq);
    }

    private static ChallengeReevaluator.Status run(ChallengeReevaluator r, int frames) {
        ChallengeReevaluator.Status s = ChallengeReevaluator.Status.CONTINUE;
        for (int i = 0; i < frames && s == ChallengeReevaluator.Status.CONTINUE; i++) {
            s = r.onFrame(frame(i), true);
        }
        return s;
    }

    @Test
    void test_attack_votes_from_two_different_voters_on_different_frames_fail_the_challenge() {
        PadVoter v1 = voter("V1", s -> s == 3 ? Vote.Label.ATTACK : Vote.Label.LIVE);
        PadVoter v2 = voter("V2", s -> s == 6 ? Vote.Label.ATTACK : Vote.Label.LIVE);
        PadVoter v5 = voter("V5", s -> Vote.Label.LIVE);
        ChallengeReevaluator r = new ChallengeReevaluator(Arrays.asList(v1, v2, v5), Collections.<String>emptySet(), 8, null);
        assertEquals(ChallengeReevaluator.Status.PAD_FAIL, run(r, 12), "ATTACK on frame 3 (V1) and frame 6 (V2) must add up");
        assertEquals(ChallengeReevaluator.Finish.PAD_FAIL, r.finish());
    }

    @Test
    void test_two_attack_votes_further_apart_than_the_window_do_not_add_up() {
        PadVoter v1 = voter("V1", s -> s == 1 ? Vote.Label.ATTACK : Vote.Label.LIVE);
        PadVoter v2 = voter("V2", s -> s == 20 ? Vote.Label.ATTACK : Vote.Label.LIVE);
        ChallengeReevaluator r = new ChallengeReevaluator(Arrays.asList(v1, v2), Collections.<String>emptySet(), 8, null);
        assertEquals(ChallengeReevaluator.Status.CONTINUE, run(r, 30));
        assertEquals(ChallengeReevaluator.Finish.OK, r.finish());
    }

    @Test
    void test_one_voter_attacking_on_many_frames_is_still_one_voter() {
        PadVoter v1 = voter("V1", s -> Vote.Label.ATTACK);
        PadVoter v2 = voter("V2", s -> Vote.Label.LIVE);
        ChallengeReevaluator r = new ChallengeReevaluator(Arrays.asList(v1, v2), Collections.<String>emptySet(), 8, null);
        assertEquals(ChallengeReevaluator.Status.CONTINUE, run(r, 20));
    }

    @Test
    void test_the_strict_voter_clearing_on_a_single_frame_is_not_enough() {
        PadVoter strict = voter("V4", s -> s == 4 ? Vote.Label.LIVE : Vote.Label.ABSTAIN);
        PadVoter other = voter("V5", s -> Vote.Label.LIVE);
        ChallengeReevaluator r = new ChallengeReevaluator(Arrays.asList(strict, other), Collections.<String>emptySet(), 8, "V4");
        run(r, 12);
        assertEquals(ChallengeReevaluator.Finish.STRICT_NOT_CLEARED, r.finish(), "one LIVE frame must not clear the strict voter");

        PadVoter twice = voter("V4", s -> s == 4 || s == 7 ? Vote.Label.LIVE : Vote.Label.ABSTAIN);
        ChallengeReevaluator ok = new ChallengeReevaluator(Arrays.asList(twice, other), Collections.<String>emptySet(), 8, "V4");
        run(ok, 12);
        assertEquals(ChallengeReevaluator.Finish.OK, ok.finish(), "two LIVE frames clear it");
    }
}
