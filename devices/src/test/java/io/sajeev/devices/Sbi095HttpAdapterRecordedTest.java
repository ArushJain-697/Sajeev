// #36 (WS2) | Test: test_095_stream_and_capture_parse | Uses responses recorded from MockMDS (serviceVersion 0.9.5).
package io.sajeev.devices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sajeev.api.DeviceInfo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

class Sbi095HttpAdapterRecordedTest {

    private static byte[] read(String name) throws Exception {
        return Files.readAllBytes(Paths.get("../testdata/recorded-095", name));
    }

    @Test
    void test_095_stream_and_capture_parse() throws Exception {
        Sbi095HttpAdapter a = new Sbi095HttpAdapter("127.0.0.1", 4501, 4600, 1000);
        List<DeviceInfo> devices = a.parseDiscovery(4501, new ObjectMapper().readTree(read("discovery-face.json")));
        assertTrue(!devices.isEmpty(), "no 0.9.5 face device parsed");
        assertEquals(DeviceInfo.Level.L0, devices.get(0).level());
        byte[] record = a.parseCaptureRecord(read("capture-face.json"));
        assertEquals("FAC", new String(record, 0, 3, StandardCharsets.ISO_8859_1), "ISO 19794-5 face magic");
    }

    @Test
    void test_095_stream_frames_parse_from_recording() throws Exception {
        io.sajeev.frame.MjpegParser p = new io.sajeev.frame.MjpegParser(
                new java.io.ByteArrayInputStream(read("stream-face.mjpeg")), "BoundaryString");
        int n = 0;
        byte[] jpeg;
        while ((jpeg = p.next()) != null) {
            assertEquals((byte) 0xFF, jpeg[0], "frame should start with a JPEG marker");
            n++;
        }
        assertTrue(n >= 1, "at least one frame expected from the recording, got " + n);
    }

    @Test
    void test_095_adapter_refuses_a_1_0_only_device() throws Exception {
        Sbi095HttpAdapter a = new Sbi095HttpAdapter("127.0.0.1", 4501, 4600, 1000);
        String oneZero = "[{\"deviceId\":\"9\",\"certification\":\"L0\",\"serviceVersion\":\"1.0\","
                + "\"digitalId\":\"eyJ0eXBlIjoiRmFjZSJ9\",\"error\":{\"errorCode\":\"0\"}}]";
        List<DeviceInfo> devices = a.parseDiscovery(4501, new ObjectMapper().readTree(oneZero));
        assertTrue(devices.isEmpty(), "a serviceVersion 1.0 device must not be accepted by the 0.9.5 adapter");
    }
}
