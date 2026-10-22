// #35 (WS2) | Bible §6.1 | SBI 1.0 over HTTP: discovery (/device), info (/info), stream (/stream), capture (/capture).
// Java 11 only. Talks to MOSIP's MockMDS or a real SBI device on the local ports 4501-4600.
package io.sajeev.devices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sajeev.api.DeviceCapabilities;
import io.sajeev.api.DeviceException;
import io.sajeev.api.DeviceInfo;
import io.sajeev.api.ErrorState;
import io.sajeev.api.FaceCaptureDevice;
import io.sajeev.api.Frame;
import io.sajeev.api.FrameSource;
import io.sajeev.api.StreamHealth;
import io.sajeev.frame.FrameGuard;
import io.sajeev.frame.MjpegParser;
import io.sajeev.frame.ProcessingMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** SBI 1.0 adapter. The device's own preview is not touched: we only read its stream (Bible §6.10). */
public class Sbi10HttpAdapter implements FaceCaptureDevice {

    /** Where a discovered device answers. */
    static final class Endpoint {
        final int port;
        final String deviceId;
        final String certification;
        final String digitalType;
        final String deviceCode;

        Endpoint(int port, String deviceId, String certification, String digitalType, String deviceCode) {
            this.port = port;
            this.deviceId = deviceId;
            this.certification = certification;
            this.digitalType = digitalType;
            this.deviceCode = deviceCode;
        }
    }

    private final String host;
    private final int minPort;
    private final int maxPort;
    private final int timeoutMs;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Endpoint> known = new HashMap<>();

    /**
     * @param host the host to scan, normally 127.0.0.1
     * @param minPort first port to scan (4501 in the client)
     * @param maxPort last port to scan (4600)
     * @param timeoutMs socket timeout in milliseconds
     */
    public Sbi10HttpAdapter(String host, int minPort, int maxPort, int timeoutMs) {
        this.host = host;
        this.minPort = minPort;
        this.maxPort = maxPort;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Scan the port range. Observed on 22 Oct 2026: the first MOSIPDISC right after MockMDS starts sometimes
     * returns an empty list, while a second call returns the device. The cause is not confirmed, so discovery
     * retries up to three times before reporting nothing (see BUILD_LOG #35).
     */
    @Override
    public List<DeviceInfo> discover() {
        List<DeviceInfo> out = new ArrayList<>();
        for (int attempt = 0; attempt < 3 && out.isEmpty(); attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            out = scanOnce();
        }
        return out;
    }

    /** Hook for subclasses: which discovered devices this adapter accepts (default: all face devices). */
    protected boolean acceptsDevice(JsonNode d) {
        return true;
    }

    /**
     * Parse a discovery response (a JSON array) from one port. Records the endpoints in {@code known}.
     * Package-private so the recorded-response tests can call it without a live device.
     */
    List<DeviceInfo> parseDiscovery(int port, JsonNode arr) {
        List<DeviceInfo> out = new ArrayList<>();
        for (JsonNode d : arr) {
            if (!"0".equals(d.path("error").path("errorCode").asText()) || !acceptsDevice(d)) {
                continue;
            }
            String id = d.path("deviceId").asText();
            String cert = d.path("certification").asText();
            String type = digitalType(d.path("digitalId").asText());
            if (!"Face".equals(type)) {
                continue;
            }
            DeviceInfo.Level level = "L1".equals(cert) ? DeviceInfo.Level.L1
                    : "L0".equals(cert) ? DeviceInfo.Level.L0 : null;
            if (level == null) {
                continue; // unknown certification is not listed rather than guessed
            }
            String key = port + ":" + id;
            known.put(key, new Endpoint(port, id, cert, type, d.path("deviceCode").asText()));
            out.add(new DeviceInfo(key, d.path("deviceCode").asText(), level));
        }
        return out;
    }

    private List<DeviceInfo> scanOnce() {
        List<DeviceInfo> out = new ArrayList<>();
        known.clear();
        String body = "{\"type\":\"Biometric Device\",\"modality\":\"Face\"}";
        for (int port = minPort; port <= maxPort; port++) {
            try {
                RawSbiHttp.Response r = RawSbiHttp.request(host, port, "MOSIPDISC", "/device", body, timeoutMs);
                if (r.status != 200) {
                    continue;
                }
                out.addAll(parseDiscovery(port, json.readTree(r.body)));
            } catch (IOException e) {
                // nothing listening on this port; keep scanning
            }
        }
        return out;
    }

    @Override
    public Optional<DeviceInfo> info(String deviceId) {
        Endpoint e = known.get(deviceId);
        if (e == null) {
            return Optional.empty();
        }
        DeviceInfo.Level level = "L1".equals(e.certification) ? DeviceInfo.Level.L1 : DeviceInfo.Level.L0;
        return Optional.of(new DeviceInfo(deviceId, e.deviceCode, level));
    }

    @Override
    public DeviceCapabilities capabilities(String deviceId) {
        if (!known.containsKey(deviceId)) {
            return new DeviceCapabilities(false, false, 0);
        }
        // SBI 1.0 face devices expose /stream and /capture. Measured fps is only known after a stream is read.
        return new DeviceCapabilities(true, true, 0);
    }

    @Override
    public FrameSource openStream(String deviceId) throws DeviceException {
        Endpoint e = endpoint(deviceId);
        String body = "{\"deviceId\":\"" + e.deviceId + "\",\"deviceSubId\":\"0\",\"timeout\":\"" + timeoutMs + "\"}";
        try {
            RawSbiHttp.StreamHandle h = RawSbiHttp.openStream(host, e.port, "/stream", body, timeoutMs);
            if (h.status != 200) {
                h.close();
                throw new DeviceException(ErrorState.DEVICE_UNAVAILABLE, "stream HTTP " + h.status);
            }
            return new SbiFrameSource(h);
        } catch (IOException ex) {
            throw new DeviceException(ErrorState.DEVICE_DISCONNECTED, "stream failed: " + ex.getMessage());
        }
    }

    @Override
    public Frame capture(String deviceId) throws DeviceException {
        byte[] record = captureRecord(deviceId);
        // The face record carries JPEG 2000 (JP2) in the MockMDS output. Decoding it is wired with the
        // Android/desktop decoders in #51/#54. Until then this refuses rather than guessing.
        throw new DeviceException(ErrorState.INVALID_FRAME_DATA,
                "rCapture returned an ISO 19794-5 record (" + record.length + " bytes); JP2 decoding not wired yet (#51/#54)");
    }

    /**
     * rCapture: the raw signed face record, as the device returns it.
     * @param deviceId a device id from discover()
     * @return the record bytes, decoded from the device's JSON payload
     * @throws DeviceException on a device error or a malformed response
     */
    public byte[] captureRecord(String deviceId) throws DeviceException {
        Endpoint e = endpoint(deviceId);
        String body = "{\"env\":\"Staging\",\"purpose\":\"Registration\",\"specVersion\":\"0.9.5\","
                + "\"timeout\":\"" + timeoutMs + "\",\"captureTime\":\"2026-10-22T10:00:00.000Z\","
                + "\"transactionId\":\"0123456789abcdef0123456789abcdef\",\"domainUri\":\"http://localhost\","
                + "\"bio\":[{\"type\":\"Face\",\"count\":\"1\",\"bioSubType\":[\"Full Face\"],\"requestedScore\":\"0\","
                + "\"deviceId\":\"" + e.deviceId + "\",\"deviceSubId\":\"0\",\"previousHash\":\"\",\"exception\":[]}],"
                + "\"customOpts\":[]}";
        try {
            RawSbiHttp.Response r = RawSbiHttp.request(host, e.port, "RCAPTURE", "/capture", body, timeoutMs);
            return parseCaptureRecord(r.body);
        } catch (IOException ex) {
            throw new DeviceException(ErrorState.DEVICE_DISCONNECTED, "rCapture failed: " + ex.getMessage());
        }
    }

    /**
     * Parse an rCapture response body into the raw face record bytes (the JWT payload's bioValue).
     * Package-private so recorded responses can be tested offline.
     */
    byte[] parseCaptureRecord(byte[] body) throws DeviceException, IOException {
        JsonNode b = json.readTree(body).path("biometrics").path(0);
        String code = b.path("error").path("errorCode").asText();
        if (!"0".equals(code)) {
            throw new DeviceException(ErrorState.DEVICE_UNAVAILABLE, "rCapture error " + code);
        }
        String[] parts = b.path("data").asText().split("\\.");
        if (parts.length != 3) {
            throw new DeviceException(ErrorState.INVALID_FRAME_DATA, "rCapture data is not a signed token");
        }
        JsonNode payload = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
        return Base64.getUrlDecoder().decode(payload.path("bioValue").asText());
    }

    @Override
    public void close(String deviceId) {
        // SBI requests are one connection each, so there is nothing to release per device.
    }

    private Endpoint endpoint(String deviceId) throws DeviceException {
        Endpoint e = known.get(deviceId);
        if (e == null) {
            throw new DeviceException(ErrorState.DEVICE_UNAVAILABLE, "unknown device " + deviceId + "; run discover()");
        }
        return e;
    }

    /** The digital ID is base64 JSON; its "type" says Face, Finger or Iris. */
    private String digitalType(String digitalId) {
        try {
            JsonNode n = json.readTree(Base64.getUrlDecoder().decode(digitalId.replace('+', '-').replace('/', '_')));
            return n.path("type").asText("");
        } catch (IOException | IllegalArgumentException e) {
            return "";
        }
    }

    /** Frames from an open SBI stream. Arrival time is measured here (System.nanoTime); fps comes from FrameGuard. */
    private static final class SbiFrameSource implements FrameSource {
        private final RawSbiHttp.StreamHandle handle;
        private final MjpegParser parser;
        private final FrameGuard guard;
        private long seq;

        SbiFrameSource(RawSbiHttp.StreamHandle handle) throws IOException {
            this.handle = handle;
            this.parser = new MjpegParser(handle.body(), "BoundaryString");
            // minFps 0.1: this source only reports what it measures; the policy decides what is too slow
            this.guard = new FrameGuard(System::nanoTime, 0.1, ProcessingMode.CONTINUOUS, 1);
        }

        @Override
        public Optional<Frame> next(long timeoutMillis) throws DeviceException {
            byte[] jpeg = parser.next(); // a broken stream throws DEVICE_DISCONNECTED; FrameGuard watches the gaps
            if (jpeg == null) {
                throw new DeviceException(ErrorState.DEVICE_DISCONNECTED, "stream ended");
            }
            guard.onArrival(true);
            return Optional.of(new Frame(jpeg, System.nanoTime(), seq++));
        }

        @Override
        public StreamHealth health() {
            return new StreamHealth(guard.measuredFps(), seq, 0L);
        }

        @Override
        public void close() {
            try {
                handle.close();
            } catch (IOException e) {
                // already closed
            }
        }
    }
}
