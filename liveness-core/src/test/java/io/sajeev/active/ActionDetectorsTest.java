// #25 (WS1) | Tests named as claims. SCRIPTED feature sequences (the spec's own method), not real people.
package io.sajeev.active;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sajeev.api.ChallengeType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ActionDetectorsTest {

    /** neutral = {yaw, pitch, ear, aperture, mouthWidth, gazeX, gazeY} */
    private static final double[] NEUTRAL = {0.0, 0.55, 0.25, 0.05, 0.40, 0.0, 0.0};
    private static final ActionThresholds T = ActionThresholds.defaults();

    private static List<FrameFeatures> script(double fps, double seconds, DoubleFunction<double[]> at) {
        List<FrameFeatures> out = new ArrayList<>();
        int frames = (int) Math.round(seconds * fps);
        for (int i = 0; i < frames; i++) {
            double t = i / fps;
            double[] v = at.apply(t);
            out.add(new FrameFeatures(i, (long) (t * 1e9), v[0], v[1], v[2], v[3], v[4], v[5], v[6]));
        }
        return out;
    }

    private static double[] with(int idx, double value) {
        double[] v = NEUTRAL.clone();
        v[idx] = value;
        return v;
    }

    /** The scripted action for a type: neutral for 1 s, the action held 1 s (blink: a 0.2 s dip), neutral again. */
    private static DoubleFunction<double[]> action(ChallengeType type) {
        return t -> {
            boolean on = t >= 1.0 && t < 2.0;
            switch (type) {
                case SMILE: return on ? with(4, 0.40 * 1.4) : NEUTRAL;
                case OPEN_MOUTH: return on ? with(3, 0.25) : NEUTRAL;
                case TURN_HEAD_LEFT: return on ? with(0, 0.35) : NEUTRAL;
                case TURN_HEAD_RIGHT: return on ? with(0, -0.35) : NEUTRAL;
                case LOOK_LEFT: return on ? with(5, 0.15) : NEUTRAL;
                case LOOK_RIGHT: return on ? with(5, -0.15) : NEUTRAL;
                case LOOK_UP: { double[] v = with(6, -0.09); v[1] = 0.45; return on ? v : NEUTRAL; }
                case LOOK_DOWN: { double[] v = with(6, 0.06); v[1] = 0.66; return on ? v : NEUTRAL; }
                case EYES_CLOSED_HOLD: return on ? with(2, 0.04) : NEUTRAL;
                case BLINK: return (t >= 1.0 && t < 1.2) ? with(2, 0.05) : NEUTRAL;
                default: throw new IllegalArgumentException();
            }
        };
    }

    private static boolean fires(ChallengeType type, double fps, DoubleFunction<double[]> seq) {
        List<FrameFeatures> frames = script(fps, 3.0, seq);
        List<FrameFeatures> first = new ArrayList<>();
        for (FrameFeatures f : frames) {
            if (f.arrivalNanos() < 1_000_000_000L) {
                first.add(f);
            }
        }
        ActionDetectors.Baseline b = ActionDetectors.Baseline.of(first);
        ActionDetectors.Detector d = ActionDetectors.create(type, b, T);
        boolean fired = false;
        for (FrameFeatures f : frames) {
            fired |= d.update(f);
        }
        return fired;
    }

    @ParameterizedTest
    @EnumSource(ChallengeType.class)
    void test_each_type_is_detected_at_the_fps_it_supports(ChallengeType type) {
        // Each type claims 3 fps, except BLINK which claims 8 (Bible §6.6, BUILD_MAP #61): test BLINK at 10 and 15 only.
        double[] rates = type == ChallengeType.BLINK ? new double[] {10, 15} : new double[] {3, 10, 15};
        for (double fps : rates) {
            assertTrue(fires(type, fps, action(type)), type + " not detected at " + fps + " fps");
            assertFalse(fires(type, fps, t -> NEUTRAL), type + " fired on a neutral face at " + fps + " fps");
        }
    }

    @Test
    void test_smile_requires_two_held_frames() {
        // one smiling frame at 10 fps is not a held smile; two in a row are
        DoubleFunction<double[]> one = t -> (t >= 1.0 && t < 1.05) ? with(4, 0.56) : NEUTRAL;
        DoubleFunction<double[]> two = t -> (t >= 1.0 && t < 1.15) ? with(4, 0.56) : NEUTRAL;
        assertFalse(fires(ChallengeType.SMILE, 10, one));
        assertTrue(fires(ChallengeType.SMILE, 10, two));
    }

    @Test
    void test_look_up_uses_pitch_not_gaze_alone() {
        DoubleFunction<double[]> gazeOnly = t -> (t >= 1.0 && t < 2.0) ? with(6, -0.09) : NEUTRAL;
        assertFalse(fires(ChallengeType.LOOK_UP, 10, gazeOnly), "gaze alone must not satisfy LOOK_UP");
        assertTrue(fires(ChallengeType.LOOK_UP, 10, action(ChallengeType.LOOK_UP)));
    }

    @Test
    void test_turn_left_means_users_left_on_mirrored_fixture() {
        // Positive yaw is the person's own left. A mirror flips the sign: the same turn must then count as RIGHT.
        DoubleFunction<double[]> turnLeft = action(ChallengeType.TURN_HEAD_LEFT);
        DoubleFunction<double[]> mirrored = t -> {
            double[] v = turnLeft.apply(t).clone();
            v[0] = -v[0];
            return v;
        };
        assertTrue(fires(ChallengeType.TURN_HEAD_LEFT, 10, turnLeft));
        assertFalse(fires(ChallengeType.TURN_HEAD_RIGHT, 10, turnLeft));
        assertTrue(fires(ChallengeType.TURN_HEAD_RIGHT, 10, mirrored));
        assertFalse(fires(ChallengeType.TURN_HEAD_LEFT, 10, mirrored));
    }

    @Test
    void test_a_blink_is_not_an_eyes_closed_hold() {
        assertFalse(fires(ChallengeType.EYES_CLOSED_HOLD, 15, action(ChallengeType.BLINK)),
                "a 0.2 s blink at 15 fps is about 3 closed frames; the hold needs the eyes to stay closed and then reopen");
    }

    @Test
    void test_baseline_is_the_median_of_the_first_second() {
        List<FrameFeatures> frames = script(10, 1.0, t -> t < 0.15 ? with(2, 0.01) : NEUTRAL); // two outliers
        ActionDetectors.Baseline b = ActionDetectors.Baseline.of(frames);
        ActionDetectors.Detector blink = ActionDetectors.create(ChallengeType.BLINK, b, T);
        assertFalse(blink.update(new FrameFeatures(99, 0, 0, 0.55, 0.25, 0.05, 0.40, 0, 0)), "neutral EAR must not read as a dip");
        assertEquals(10, frames.size());
    }
}
