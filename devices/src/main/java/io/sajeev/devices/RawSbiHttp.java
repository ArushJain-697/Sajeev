// #35 (WS2) | Bible §6.1 | Minimal HTTP/1.1 client for SBI custom methods (MOSIPDISC, MOSIPDINFO, STREAM, RCAPTURE).
// HttpURLConnection refuses custom methods, so this speaks raw HTTP over a socket, as the MOSIP mock does.
// Java 11 only. Responses are read with bounds (see MAX_BODY).
package io.sajeev.devices;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** One request, one response, connection closed. Enough for SBI discovery, info and capture. */
final class RawSbiHttp {

    /** Largest response body accepted for a non-stream request (2 MB). */
    static final int MAX_BODY = 2 * 1024 * 1024;

    /** A parsed response. The body is kept as bytes; the stream is returned open by openStream. */
    static final class Response {
        final int status;
        final Map<String, String> headers;
        final byte[] body;

        Response(int status, Map<String, String> headers, byte[] body) {
            this.status = status;
            this.headers = headers;
            this.body = body;
        }
    }

    private RawSbiHttp() {
    }

    /** Send a request and read the whole response body (bounded by MAX_BODY). */
    static Response request(String host, int port, String method, String path, String body, int timeoutMs)
            throws IOException {
        try (Socket s = connect(host, port, timeoutMs)) {
            send(s.getOutputStream(), method, path, host, port, body);
            InputStream in = s.getInputStream();
            Map<String, String> headers = new LinkedHashMap<>();
            int status = readStatusAndHeaders(in, headers);
            byte[] data = readBounded(in, MAX_BODY);
            return new Response(status, headers, data);
        }
    }

    /**
     * Send a STREAM request and return the open socket and its body stream. The caller reads
     * multipart JPEG parts from it (MjpegParser) and closes the socket.
     */
    static StreamHandle openStream(String host, int port, String path, String body, int timeoutMs) throws IOException {
        Socket s = connect(host, port, timeoutMs);
        try {
            send(s.getOutputStream(), "STREAM", path, host, port, body);
            Map<String, String> headers = new LinkedHashMap<>();
            int status = readStatusAndHeaders(s.getInputStream(), headers);
            return new StreamHandle(s, status, headers);
        } catch (IOException e) {
            s.close();
            throw e;
        }
    }

    /** An open streaming response. */
    static final class StreamHandle implements AutoCloseable {
        final Socket socket;
        final int status;
        final Map<String, String> headers;

        StreamHandle(Socket socket, int status, Map<String, String> headers) {
            this.socket = socket;
            this.status = status;
            this.headers = headers;
        }

        InputStream body() throws IOException {
            return socket.getInputStream();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static Socket connect(String host, int port, int timeoutMs) throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), timeoutMs);
        s.setSoTimeout(timeoutMs);
        return s;
    }

    private static void send(OutputStream out, String method, String path, String host, int port, String body)
            throws IOException {
        byte[] b = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        String head = method + " " + path + " HTTP/1.1\r\n"
                + "Host: " + host + ":" + port + "\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + b.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(b);
        out.flush();
    }

    private static int readStatusAndHeaders(InputStream in, Map<String, String> headers) throws IOException {
        String status = readLine(in);
        if (status == null || !status.startsWith("HTTP/")) {
            throw new IOException("not an HTTP response: " + status);
        }
        String[] parts = status.split(" ");
        int code = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        String line = readLine(in);
        while (line != null && !line.isEmpty()) {
            int c = line.indexOf(':');
            if (c > 0) {
                headers.put(line.substring(0, c).trim().toLowerCase(), line.substring(c + 1).trim());
            }
            line = readLine(in);
        }
        return code;
    }

    private static String readLine(InputStream in) throws IOException {
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
            if (out.size() > 8192) {
                throw new IOException("header line too long");
            }
        }
        return any ? out.toString(StandardCharsets.ISO_8859_1.name()) : null;
    }

    private static byte[] readBounded(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
            if (out.size() > max) {
                throw new IOException("response body over " + max + " bytes");
            }
        }
        return out.toByteArray();
    }
}
