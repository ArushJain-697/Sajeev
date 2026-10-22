// #15 (WS1) | Bible §6.1 | Tests named as claims. Fake clock, so timing is exact.
package io.sajeev.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class FrameGuardTest {

    /** Feed frames at the given fps for two seconds and return the guard's measured fps. */
    private static double fpsAt(double fps) {
        long[] now = {0L};
        FrameGuard g = new FrameGuard(() -> now[0], 1.0, ProcessingMode.CONTINUOUS, 1);
        long step = (long) (1_000_000_000.0 / fps);
        for (int i = 0; i < 2 * fps; i++) {
            g.onArrival(true);
            now[0] += step;
        }
        // measure at the time of the last arrival
        now[0] -= step;
        return g.measuredFps();
    }

    @Test
    void test_measured_fps_within_10pct_at_3_10_15_fps() {
        for (double fps : new double[] {3.0, 10.0, 15.0}) {
            double m = fpsAt(fps);
            assertTrue(Math.abs(m - fps) / fps <= 0.10, "fps " + fps + " measured " + m);
        }
    }

    @Test
    void test_stream_stalled_after_2s_without_frames() {
        long[] now = {0L};
        FrameGuard g = new FrameGuard(() -> now[0], 1.0, ProcessingMode.CONTINUOUS, 1);
        g.onArrival(true);
        now[0] = 1_900_000_000L;
        assertFalse(g.flags().contains(FrameFlag.STREAM_STALLED));
        now[0] = 2_100_000_000L;
        assertTrue(g.flags().contains(FrameFlag.STREAM_STALLED));
    }

    @Test
    void test_over_20pct_bad_frames_reports_invalid_frame_data() {
        long[] now = {0L};
        FrameGuard g = new FrameGuard(() -> now[0], 1.0, ProcessingMode.CONTINUOUS, 1);
        for (int i = 0; i < 10; i++) {
            g.onArrival(i % 4 != 0); // 25% bad
            now[0] += 100_000_000L;
        }
        Set<FrameFlag> f = g.flags();
        assertTrue(f.contains(FrameFlag.INVALID_FRAME_DATA));
    }

    @Test
    void test_under_20pct_bad_frames_is_not_flagged() {
        long[] now = {0L};
        FrameGuard g = new FrameGuard(() -> now[0], 1.0, ProcessingMode.CONTINUOUS, 1);
        for (int i = 0; i < 10; i++) {
            g.onArrival(i != 0); // 10% bad
            now[0] += 100_000_000L;
        }
        assertFalse(g.flags().contains(FrameFlag.INVALID_FRAME_DATA));
    }

    @Test
    void test_bursty_arrival_is_flagged() {
        long[] now = {0L};
        FrameGuard g = new FrameGuard(() -> now[0], 1.0, ProcessingMode.CONTINUOUS, 1);
        // clumps of three frames 1 ms apart, then a 500 ms gap
        for (int c = 0; c < 4; c++) {
            for (int k = 0; k < 3; k++) {
                g.onArrival(true);
                now[0] += 1_000_000L;
            }
            now[0] += 500_000_000L;
        }
        assertTrue(g.flags().contains(FrameFlag.STREAM_BURSTY));
    }

    @Test
    void test_steady_stream_is_not_bursty() {
        assertFalse(fpsFlags(10.0).contains(FrameFlag.STREAM_BURSTY));
    }

    @Test
    void test_low_fps_is_device_inadequate() {
        Set<FrameFlag> f = fpsFlags(2.0);
        assertTrue(f.contains(FrameFlag.DEVICE_INADEQUATE));
    }

    @Test
    void test_periodic_mode_processes_every_nth_frame() {
        long[] now = {0L};
        FrameGuard g = new FrameGuard(() -> now[0], 1.0, ProcessingMode.PERIODIC, 3);
        int processed = 0;
        for (int i = 0; i < 9; i++) {
            if (g.onArrival(true)) {
                processed++;
            }
            now[0] += 100_000_000L;
        }
        assertEquals(3, processed);
    }

    private static Set<FrameFlag> fpsFlags(double fps) {
        long[] now = {0L};
        FrameGuard g = new FrameGuard(() -> now[0], 3.0, ProcessingMode.CONTINUOUS, 1);
        long step = (long) (1_000_000_000.0 / fps);
        for (int i = 0; i < 2 * fps; i++) {
            g.onArrival(true);
            now[0] += step;
        }
        now[0] -= step;
        return g.flags();
    }
}
