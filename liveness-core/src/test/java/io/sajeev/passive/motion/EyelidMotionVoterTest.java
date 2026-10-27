// Red team 2 | Tests named as claims for the eyelid motion voter. Series are constructed, not measured.
package io.sajeev.passive.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sajeev.active.FrameFeatures;
import io.sajeev.api.Vote;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;
import org.junit.jupiter.api.Test;

class EyelidMotionVoterTest {

    private static final long FRAME = 33_333_333L; // 30 fps
    private static final EyelidMotionVoter V = new EyelidMotionVoter(0.06, 2_000_000_000L);

    private static List<FrameFeatures> series(int frames, IntToDoubleFunction ear) {
        List<FrameFeatures> l = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            l.add(new FrameFeatures(i, i * FRAME, 0, 0, ear.applyAsDouble(i), 0.1, 0.4, 0, 0));
        }
        return l;
    }

    @Test
    void test_a_picture_that_only_shakes_with_the_hand_gets_no_live_vote() {
        // the real attack clip: eye aspect ratio 0.31..0.34 over 8.6 s
        Vote v = V.vote(series(257, i -> 0.325 + 0.01 * Math.sin(i / 7.0)));
        assertEquals(Vote.Label.ABSTAIN, v.label());
    }

    @Test
    void test_a_blink_gives_a_live_vote() {
        Vote v = V.vote(series(150, i -> (i >= 70 && i <= 74) ? 0.08 : 0.30));
        assertEquals(Vote.Label.LIVE, v.label());
    }

    @Test
    void test_stillness_is_never_called_an_attack() {
        // a person staring at the camera: no vote either way, so the client asks for the challenge
        Vote v = V.vote(series(240, i -> 0.30));
        assertEquals(Vote.Label.ABSTAIN, v.label());
        assertTrue(v.label() != Vote.Label.ATTACK);
    }

    @Test
    void test_one_bad_landmark_frame_cannot_create_eyelid_motion() {
        Vote v = V.vote(series(150, i -> i == 60 ? 0.02 : 0.30));
        assertEquals(Vote.Label.ABSTAIN, v.label());
    }

    @Test
    void test_too_short_an_observation_is_not_judged() {
        Vote v = V.vote(series(30, i -> i < 15 ? 0.30 : 0.10)); // 1 s: a real blink but not enough watching
        assertEquals(Vote.Label.ABSTAIN, v.label());
    }

    @Test
    void test_unmeasurable_eyes_abstain() {
        Vote v = V.vote(series(150, i -> Double.NaN));
        assertEquals(Vote.Label.ABSTAIN, v.label());
    }
}
