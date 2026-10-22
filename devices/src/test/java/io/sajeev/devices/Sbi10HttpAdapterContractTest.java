// #35 (WS2) | Contract tests against MOSIP's unmodified MockMDS (local port 4501).
// They skip with a message when no MockMDS is listening. Start it with the command in BUILD_LOG.md.
package io.sajeev.devices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.sajeev.api.DeviceInfo;
import io.sajeev.api.Frame;
import io.sajeev.api.FrameSource;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class Sbi10HttpAdapterContractTest {

    private static boolean mockMdsUp() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 4501), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void test_discovers_mock_device() {
        assumeTrue(mockMdsUp(), "MockMDS not running on 127.0.0.1:4501");
        Sbi10HttpAdapter a = new Sbi10HttpAdapter("127.0.0.1", 4501, 4600, 15000);
        List<DeviceInfo> found = a.discover();
        assertTrue(!found.isEmpty(), "no face device found");
        assertEquals(DeviceInfo.Level.L0, found.get(0).level());
    }

    @Test
    void test_stream_returns_frames_at_least_every_3s() throws Exception {
        assumeTrue(mockMdsUp(), "MockMDS not running on 127.0.0.1:4501");
        Sbi10HttpAdapter a = new Sbi10HttpAdapter("127.0.0.1", 4501, 4600, 15000);
        String id = a.discover().get(0).id();
        try (FrameSource src = a.openStream(id)) {
            Optional<Frame> f1 = src.next(10_000);
            Optional<Frame> f2 = src.next(10_000);
            assertTrue(f1.isPresent() && f2.isPresent(), "two frames expected");
            long gapMs = (f2.get().arrivalNanos() - f1.get().arrivalNanos()) / 1_000_000L;
            assertTrue(gapMs <= 3_500L, "frame gap " + gapMs + " ms; MockMDS promises about 3 s");
        }
    }

    @Test
    void test_rcapture_returns_signed_face_record() throws Exception {
        assumeTrue(mockMdsUp(), "MockMDS not running on 127.0.0.1:4501");
        Sbi10HttpAdapter a = new Sbi10HttpAdapter("127.0.0.1", 4501, 4600, 15000);
        String id = a.discover().get(0).id();
        byte[] record = a.captureRecord(id);
        assertTrue(record.length > 1000, "record too small: " + record.length);
        assertEquals("FAC", new String(record, 0, 3, "ISO-8859-1"), "ISO 19794-5 face magic");
    }
}
