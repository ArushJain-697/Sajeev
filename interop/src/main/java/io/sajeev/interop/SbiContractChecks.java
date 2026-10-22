// #39 (WS4) | SBI contract checks for a face device over HTTP (MOSIPDISC, MOSIPDINFO, STREAM, RCAPTURE). Java 11 only.
// Independent of the liveness engine. Parsing checks work on bytes, so recorded responses can be checked too.
package io.sajeev.interop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** The checks a face device must pass to be usable by the registration client. */
public final class SbiContractChecks {

    /** The PDF's floor for a face stream, in frames per second. */
    public static final double MIN_FPS = 3.0;

    private static final ObjectMapper JSON = new ObjectMapper();

    private SbiContractChecks() {
    }

    // ---- parsing checks (bytes in, result out) ----

    /** Discovery: a JSON array with a Face device that has an id, an L0 or L1 certification and a decodable digital ID. */
    public static CheckResult discovery(byte[] body) {
        try {
            JsonNode arr = JSON.readTree(body);
            if (!arr.isArray() || arr.size() == 0) {
                return fail("discovery", "response is not a non-empty JSON array");
            }
            for (JsonNode d : arr) {
                JsonNode dig = digitalId(d.path("digitalId").asText());
                if (dig != null && "Face".equals(dig.path("type").asText()) && !d.path("deviceId").asText().isEmpty()) {
                    String cert = d.path("certification").asText();
                    if (!"L0".equals(cert) && !"L1".equals(cert)) {
                        return fail("discovery", "certification is '" + cert + "', expected L0 or L1");
                    }
                    return pass("discovery", "face device " + d.path("deviceId").asText() + ", " + cert
                            + ", mock=" + d.path("mock").asText("not reported"));
                }
            }
            return fail("discovery", "no Face device with a decodable digital ID");
        } catch (IOException | RuntimeException e) {
            return fail("discovery", "unreadable: " + e.getMessage());
        }
    }

    /** Info: an array whose first item has a signed deviceInfo token (three parts). */
    public static CheckResult info(byte[] body) {
        try {
            JsonNode arr = JSON.readTree(body);
            String token = arr.path(0).path("deviceInfo").asText();
            if (token.split("\\.").length != 3) {
                return fail("info", "deviceInfo is not a three-part signed token");
            }
            return pass("info", "signed deviceInfo token present");
        } catch (IOException | RuntimeException e) {
            return fail("info", "unreadable: " + e.getMessage());
        }
    }

    /** Stream rate from arrival times in nanoseconds. */
    public static CheckResult streamRate(List<Long> arrivalNanos) {
        if (arrivalNanos.size() < 3) {
            return fail("stream >= 3 fps", "only " + arrivalNanos.size() + " frames arrived");
        }
        double seconds = (arrivalNanos.get(arrivalNanos.size() - 1) - arrivalNanos.get(1)) / 1e9;
        double fps = (arrivalNanos.size() - 2) / seconds;
        String d = String.format("measured %.2f fps over %d frames", fps, arrivalNanos.size());
        return fps >= MIN_FPS ? pass("stream >= 3 fps", d) : fail("stream >= 3 fps", d + " (PDF minimum is 3)");
    }

    /** rCapture: no error, a signed token (non-empty signature part), an ISO 19794-5 face record with the FAC magic. */
    public static CheckResult rCapture(byte[] body) {
        try {
            JsonNode b = JSON.readTree(body).path("biometrics").path(0);
            String code = b.path("error").path("errorCode").asText();
            if (!"0".equals(code)) {
                return fail("rCapture signed ISO record", "device error " + code);
            }
            String[] parts = b.path("data").asText().split("\\.", -1);
            if (parts.length != 3 || parts[2].isEmpty()) {
                return fail("rCapture signed ISO record", "UNSIGNED: data is not a token with a signature part");
            }
            JsonNode payload = JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
            byte[] record = Base64.getUrlDecoder().decode(payload.path("bioValue").asText());
            if (record.length < 4 || !"FAC".equals(new String(record, 0, 3, StandardCharsets.ISO_8859_1))) {
                return fail("rCapture signed ISO record", "bioValue does not start with the ISO 19794-5 FAC magic");
            }
            return pass("rCapture signed ISO record", "signed token, FAC record of " + record.length + " bytes");
        } catch (IOException | RuntimeException e) {
            return fail("rCapture signed ISO record", "unreadable: " + e.getMessage());
        }
    }

    /** Digital ID fields the spec asks for, read from the rCapture payload's digitalId. */
    public static CheckResult digitalIdFields(byte[] captureBody) {
        try {
            JsonNode b = JSON.readTree(captureBody).path("biometrics").path(0);
            String[] parts = b.path("data").asText().split("\\.", -1);
            JsonNode payload = JSON.readTree(Base64.getUrlDecoder().decode(parts[1]));
            String[] digParts = payload.path("digitalId").asText().split("\\.", -1);
            JsonNode dig = JSON.readTree(Base64.getUrlDecoder().decode(digParts[1]));
            List<String> missing = new ArrayList<>();
            for (String f : new String[] {"serialNo", "make", "model", "type", "deviceProvider", "deviceProviderId", "dateTime"}) {
                if (dig.path(f).asText().isEmpty()) {
                    missing.add(f);
                }
            }
            return missing.isEmpty() ? pass("digital ID fields", "serialNo, make, model, type, provider, dateTime present")
                    : fail("digital ID fields", "missing " + missing);
        } catch (IOException | RuntimeException e) {
            return fail("digital ID fields", "unreadable: " + e.getMessage());
        }
    }

    private static JsonNode digitalId(String value) {
        try {
            String v = value.contains(".") ? value.split("\\.")[1] : value;
            return JSON.readTree(Base64.getUrlDecoder().decode(v.replace('+', '-').replace('/', '_')));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ---- live checks against a host and port ----

    /** Run all live checks against a device service at host:port. */
    public static List<CheckResult> runLive(String host, int port, int streamSeconds) {
        List<CheckResult> out = new ArrayList<>();
        try {
            out.add(discovery(request(host, port, "MOSIPDISC", "/device", "{\"type\":\"Biometric Device\",\"modality\":\"Face\"}")));
        } catch (IOException e) {
            out.add(fail("discovery", "no answer: " + e.getMessage()));
            return out;
        }
        try {
            out.add(info(request(host, port, "MOSIPDINFO", "/info", "{}")));
        } catch (IOException e) {
            out.add(fail("info", "no answer: " + e.getMessage()));
        }
        try {
            out.add(streamRate(streamArrivals(host, port, streamSeconds)));
        } catch (IOException e) {
            out.add(fail("stream >= 3 fps", "no stream: " + e.getMessage()));
        }
        try {
            byte[] cap = request(host, port, "RCAPTURE", "/capture", captureBody("3"));
            // A device can still be busy right after a stream (codes 703, 111, 607): wait and retry a few times
            for (int attempt = 0; attempt < 5 && isBusy(cap); attempt++) {
                Thread.sleep(2000);
                cap = request(host, port, "RCAPTURE", "/capture", captureBody("3"));
            }
            out.add(rCapture(cap));
            out.add(digitalIdFields(cap));
        } catch (IOException | InterruptedException e) {
            out.add(fail("rCapture signed ISO record", "no answer: " + e.getMessage()));
        }
        return out;
    }

    private static boolean isBusy(byte[] capture) {
        try {
            String code = JSON.readTree(capture).path("biometrics").path(0).path("error").path("errorCode").asText();
            return "703".equals(code) || "111".equals(code) || "607".equals(code);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    static String captureBody(String deviceId) {
        return "{\"env\":\"Staging\",\"purpose\":\"Registration\",\"specVersion\":\"0.9.5\",\"timeout\":\"5000\","
                + "\"captureTime\":\"2026-10-23T10:00:00.000Z\",\"transactionId\":\"0123456789abcdef0123456789abcdef\","
                + "\"domainUri\":\"http://localhost\",\"bio\":[{\"type\":\"Face\",\"count\":\"1\",\"bioSubType\":[\"Full Face\"],"
                + "\"requestedScore\":\"0\",\"deviceId\":\"" + deviceId + "\",\"deviceSubId\":\"0\",\"previousHash\":\"\","
                + "\"exception\":[]}],\"customOpts\":[]}";
    }

    private static byte[] request(String host, int port, String method, String path, String body) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 3000);
            s.setSoTimeout(15000);
            send(s.getOutputStream(), method, path, host, port, body);
            InputStream in = s.getInputStream();
            readHeaders(in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
                if (out.size() > 4_000_000) {
                    throw new IOException("response over 4 MB");
                }
            }
            return out.toByteArray();
        }
    }

    private static List<Long> streamArrivals(String host, int port, int seconds) throws IOException {
        List<Long> times = new ArrayList<>();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 3000);
            s.setSoTimeout(5000);
            send(s.getOutputStream(), "STREAM", "/stream", host, port, "{\"deviceId\":\"3\",\"deviceSubId\":\"0\",\"timeout\":\"20000\"}");
            InputStream in = s.getInputStream();
            readHeaders(in);
            long end = System.nanoTime() + seconds * 1_000_000_000L;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[65536];
            while (System.nanoTime() < end) {
                int n = in.read(chunk);
                if (n < 0) {
                    break;
                }
                buf.write(chunk, 0, n);
                byte[] all = buf.toByteArray();
                int[] consumed = {0};
                int frames = countFrames(all, consumed, times);
                if (frames > 0) {
                    buf.reset();
                    buf.write(all, consumed[0], all.length - consumed[0]);
                }
            }
        } catch (java.net.SocketTimeoutException e) {
            // a stalled stream: the arrivals so far decide the verdict
        }
        return times;
    }

    private static int countFrames(byte[] all, int[] consumed, List<Long> times) {
        String text = new String(all, StandardCharsets.ISO_8859_1);
        int pos = 0;
        int found = 0;
        while (true) {
            int cl = text.indexOf("Content-Length:", pos);
            if (cl < 0) {
                break;
            }
            int eol = text.indexOf("\r\n", cl);
            int hdrEnd = text.indexOf("\r\n\r\n", cl);
            if (eol < 0 || hdrEnd < 0) {
                break;
            }
            int len = Integer.parseInt(text.substring(cl + 15, eol).trim());
            int start = hdrEnd + 4;
            if (all.length < start + len) {
                break;
            }
            times.add(System.nanoTime());
            pos = start + len;
            consumed[0] = pos;
            found++;
        }
        return found;
    }

    private static void send(OutputStream out, String method, String path, String host, int port, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        out.write((method + " " + path + " HTTP/1.1\r\nHost: " + host + ":" + port + "\r\nContent-Type: application/json\r\n"
                + "Content-Length: " + b.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.write(b);
        out.flush();
    }

    /** Read and discard the HTTP headers: stop after the first blank line (CRLF CRLF). */
    private static void readHeaders(InputStream in) throws IOException {
        int state = 0;
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\r' && (state == 0 || state == 2)) {
                state++;
            } else if (c == '\n' && (state == 1 || state == 3)) {
                state++;
                if (state == 4) {
                    return;
                }
            } else {
                state = 0;
            }
        }
    }

    private static CheckResult pass(String n, String d) { return new CheckResult(n, CheckResult.Status.PASS, d); }

    private static CheckResult fail(String n, String d) { return new CheckResult(n, CheckResult.Status.FAIL, d); }
}
