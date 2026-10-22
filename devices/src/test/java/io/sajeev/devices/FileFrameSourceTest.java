// #15 (WS1) | Tests named as claims
package io.sajeev.devices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sajeev.api.DeviceException;
import io.sajeev.api.ErrorState;
import io.sajeev.api.Frame;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileFrameSourceTest {

    @TempDir
    Path dir;

    @Test
    void test_file_adapter_replays_frames_in_name_order_at_the_set_fps() throws Exception {
        for (int i = 0; i < 3; i++) {
            Files.write(dir.resolve(String.format("f%02d.jpg", i)), new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) i});
        }
        FileFrameSource src = new FileFrameSource(dir, 10.0);
        Optional<Frame> a = src.next(0);
        Optional<Frame> b = src.next(0);
        assertEquals(0L, a.get().sequence());
        assertEquals(100_000_000L, b.get().arrivalNanos() - a.get().arrivalNanos());
        assertTrue(src.next(0).isPresent());
        assertTrue(!src.next(0).isPresent());
    }

    @Test
    void test_file_adapter_refuses_an_empty_folder() {
        DeviceException e = assertThrows(DeviceException.class, () -> new FileFrameSource(dir, 10.0));
        assertEquals(ErrorState.DEVICE_UNAVAILABLE, e.state());
    }

    @Test
    void test_replayed_clip_rate_within_10pct() throws Exception {
        // #11: synthetic 10 s clips at 3, 10 and 15 fps (testdata/clips-synthetic, made by eval/rig/make_rate_clips.sh)
        for (int fps : new int[] {3, 10, 15}) {
            Path folder = Paths.get("../testdata/clips-synthetic/fps" + fps);
            if (!Files.isDirectory(folder)) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, "run eval/rig/make_rate_clips.sh first");
            }
            FileFrameSource src = new FileFrameSource(folder, fps);
            int n = 0;
            while (src.next(0).isPresent()) {
                n++;
            }
            double clipSeconds = n / (double) fps;
            assertTrue(Math.abs(clipSeconds - 10.0) <= 1.0, "fps " + fps + ": " + n + " frames is " + clipSeconds + " s");
        }
    }
}
