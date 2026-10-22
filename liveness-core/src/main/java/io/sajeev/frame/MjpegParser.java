// #15 (WS1) | Bible §6.1 | Parses multipart/x-mixed-replace (M-JPEG) frames. Java 11 only.
// Java bytes are signed: JPEG markers 0xFF read as -1. Every read is bounds-checked (no trusted lengths).
package io.sajeev.frame;

import io.sajeev.api.DeviceException;
import io.sajeev.api.ErrorState;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Reads JPEG parts from an M-JPEG stream, one part at a time. */
public final class MjpegParser {

    /** Largest frame we accept (guards against a bogus Content-Length). */
    static final int MAX_FRAME_BYTES = 8 * 1024 * 1024;
    /** Largest header block we accept. */
    static final int MAX_HEADER_BYTES = 4 * 1024;

    private final InputStream in;
    private final String boundary;

    /**
     * @param in the stream, positioned at the start of the multipart body
     * @param boundary the boundary string without the leading "--", for example "BoundaryString"
     */
    public MjpegParser(InputStream in, String boundary) {
        this.in = in;
        this.boundary = boundary;
    }

    /**
     * Read the next JPEG part.
     * @return the JPEG bytes, or null at a clean end of stream
     * @throws DeviceException INVALID_FRAME_DATA for a malformed part, DEVICE_DISCONNECTED for an I/O failure
     */
    public byte[] next() throws DeviceException {
        try {
            String line = readLine(MAX_HEADER_BYTES);
            while (line != null && !line.startsWith("--" + boundary)) {
                line = readLine(MAX_HEADER_BYTES);
            }
            if (line == null) {
                return null;
            }
            int length = -1;
            String h = readLine(MAX_HEADER_BYTES);
            while (h != null && !h.isEmpty()) {
                int colon = h.indexOf(':');
                if (colon > 0 && h.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                    length = parseLength(h.substring(colon + 1).trim());
                }
                h = readLine(MAX_HEADER_BYTES);
            }
            if (length < 0) {
                throw new DeviceException(ErrorState.INVALID_FRAME_DATA, "part without Content-Length");
            }
            return readExactly(length);
        } catch (IOException e) {
            throw new DeviceException(ErrorState.DEVICE_DISCONNECTED, "stream read failed: " + e.getMessage());
        }
    }

    private static int parseLength(String s) throws DeviceException {
        try {
            int n = Integer.parseInt(s);
            if (n <= 0 || n > MAX_FRAME_BYTES) {
                throw new DeviceException(ErrorState.INVALID_FRAME_DATA, "Content-Length out of range: " + n);
            }
            return n;
        } catch (NumberFormatException e) {
            throw new DeviceException(ErrorState.INVALID_FRAME_DATA, "bad Content-Length: " + s);
        }
    }

    private byte[] readExactly(int n) throws IOException, DeviceException {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(buf, off, n - off);
            if (r < 0) {
                throw new DeviceException(ErrorState.INVALID_FRAME_DATA, "stream ended inside a frame");
            }
            off += r;
        }
        return buf;
    }

    private String readLine(int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        boolean any = false;
        while ((b = in.read()) >= 0) {
            any = true;
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                out.write(b);
            }
            if (out.size() > max) {
                return out.toString(StandardCharsets.ISO_8859_1.name());
            }
        }
        if (!any) {
            return null;
        }
        return out.toString(StandardCharsets.ISO_8859_1.name());
    }
}
