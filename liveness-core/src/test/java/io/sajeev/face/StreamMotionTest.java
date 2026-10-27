// Red-team 2 fix: a stream whose pictures never change is not a live camera.
package io.sajeev.face;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sajeev.image.PixelImage;
import java.util.Random;
import org.junit.jupiter.api.Test;

class StreamMotionTest {

    private static PixelImage picture(int w, int h, long seed, int noise) {
        Random scene = new Random(7);
        Random sensor = new Random(seed);
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) {
            int g = 60 + scene.nextInt(120) + (noise == 0 ? 0 : sensor.nextInt(2 * noise + 1) - noise);
            g = Math.max(0, Math.min(255, g));
            px[i] = 0xFF000000 | (g << 16) | (g << 8) | g;
        }
        return new PixelImage(w, h, px);
    }

    @Test
    void test_identical_frames_have_zero_difference_and_are_frozen() {
        StreamMotion m = new StreamMotion(7);
        PixelImage still = picture(320, 240, 1, 0);
        for (int i = 0; i < 8; i++) {
            m.observe(still);
        }
        assertEquals(7, m.pairs());
        assertEquals(0.0, m.maxDifference(), 0.0);
        assertTrue(m.frozen(0.02, 5));
    }

    @Test
    void test_sensor_noise_between_frames_is_not_frozen() {
        StreamMotion m = new StreamMotion(7);
        for (int i = 0; i < 8; i++) {
            m.observe(picture(1280, 720, i, 3)); // +-3 grey levels of noise per pixel
        }
        assertTrue(m.maxDifference() > 0.02, "difference " + m.maxDifference());
        assertFalse(m.frozen(0.02, 5));
    }

    @Test
    void test_one_changing_pair_in_the_window_is_enough_to_not_be_frozen() {
        StreamMotion m = new StreamMotion(7);
        PixelImage still = picture(320, 240, 1, 0);
        for (int i = 0; i < 4; i++) {
            m.observe(still);
        }
        m.observe(picture(320, 240, 2, 6));
        for (int i = 0; i < 3; i++) {
            m.observe(still);
        }
        assertFalse(m.frozen(0.02, 5));
    }

    @Test
    void test_too_few_frames_say_nothing() {
        StreamMotion m = new StreamMotion(7);
        PixelImage still = picture(64, 64, 1, 0);
        m.observe(still);
        m.observe(still);
        assertFalse(m.frozen(0.02, 5), "two frames are not enough to call a stream frozen");
    }
}
