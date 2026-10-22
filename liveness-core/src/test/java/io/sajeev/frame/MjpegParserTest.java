// #15 (WS1) | Bible §6.1 | Tests named as claims
package io.sajeev.frame;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.sajeev.api.DeviceException;
import io.sajeev.api.ErrorState;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MjpegParserTest {

    private static byte[] part(byte[] jpeg) {
        String head = "--BoundaryString\r\nContent-Type: image/jpeg\r\nContent-Length: " + jpeg.length + "\r\n\r\n";
        byte[] h = head.getBytes(StandardCharsets.ISO_8859_1);
        byte[] out = new byte[h.length + jpeg.length + 2];
        System.arraycopy(h, 0, out, 0, h.length);
        System.arraycopy(jpeg, 0, out, h.length, jpeg.length);
        out[out.length - 2] = '\r';
        out[out.length - 1] = '\n';
        return out;
    }

    @Test
    void test_mjpeg_parser_reads_jpeg_bytes_including_signed_markers() throws Exception {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, 1, 2, (byte) 0xFF, (byte) 0xD9};
        byte[] stream = part(jpeg);
        MjpegParser p = new MjpegParser(new ByteArrayInputStream(stream), "BoundaryString");
        assertArrayEquals(jpeg, p.next());
        assertNull(p.next());
    }

    @Test
    void test_mjpeg_parser_refuses_a_bogus_content_length() {
        String s = "--BoundaryString\r\nContent-Length: 999999999999\r\n\r\nxx";
        MjpegParser p = new MjpegParser(new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1)), "BoundaryString");
        DeviceException e = assertThrows(DeviceException.class, p::next);
        org.junit.jupiter.api.Assertions.assertEquals(ErrorState.INVALID_FRAME_DATA, e.state());
    }

    @Test
    void test_mjpeg_parser_reports_a_truncated_frame() {
        String s = "--BoundaryString\r\nContent-Length: 10\r\n\r\nabc";
        MjpegParser p = new MjpegParser(new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1)), "BoundaryString");
        DeviceException e = assertThrows(DeviceException.class, p::next);
        org.junit.jupiter.api.Assertions.assertEquals(ErrorState.INVALID_FRAME_DATA, e.state());
    }
}
