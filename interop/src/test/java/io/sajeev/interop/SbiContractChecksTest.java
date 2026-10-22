// #39 (WS4) | Tests named as claims. A fake device server stands in for a bad device, so no real device is needed.
package io.sajeev.interop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class SbiContractChecksTest {

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    /** A fake device service: streams tiny frames at the given fps, and answers rCapture with the given data field. */
    private static ServerSocket fakeDevice(double fps, String captureData) throws Exception {
        ServerSocket server = new ServerSocket(0);
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try {
                    Socket s = server.accept();
                    new Thread(() -> serve(s, fps, captureData)).start();
                } catch (Exception e) {
                    return;
                }
            }
        });
        t.setDaemon(true);
        t.start();
        return server;
    }

    private static void serve(Socket s, double fps, String captureData) {
        try (Socket sock = s) {
            byte[] req = new byte[4096];
            int n = sock.getInputStream().read(req);
            String head = new String(req, 0, Math.max(n, 0), StandardCharsets.ISO_8859_1);
            OutputStream out = sock.getOutputStream();
            if (head.startsWith("STREAM")) {
                out.write("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=--BoundaryString\r\n\r\n".getBytes());
                byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3, (byte) 0xFF, (byte) 0xD9};
                for (int i = 0; i < 40; i++) {
                    out.write(("--BoundaryString\r\nContent-Type: image/jpeg\r\nContent-Length: " + jpeg.length + "\r\n\r\n").getBytes());
                    out.write(jpeg);
                    out.write("\r\n\r\n".getBytes());
                    out.flush();
                    Thread.sleep((long) (1000 / fps));
                }
            } else if (head.startsWith("RCAPTURE")) {
                String body = "{\"biometrics\":[{\"data\":\"" + captureData + "\",\"error\":{\"errorCode\":\"0\"}}]}";
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n" + body).getBytes());
            } else {
                out.write("HTTP/1.1 200 OK\r\n\r\n[]".getBytes());
            }
            out.flush();
        } catch (Exception e) {
            // client closed the connection: normal for the stream
        }
    }

    private static String token(boolean signed, String bioValue) {
        String payload = b64("{\"bioValue\":\"" + Base64.getUrlEncoder().withoutPadding().encodeToString(bioValue.getBytes(StandardCharsets.ISO_8859_1))
                + "\",\"digitalId\":\"h." + b64("{\"serialNo\":\"1\",\"make\":\"m\",\"model\":\"x\",\"type\":\"Face\",\"deviceProvider\":\"p\",\"deviceProviderId\":\"1\",\"dateTime\":\"d\"}") + ".s\"}");
        return b64("{\"alg\":\"RS256\"}") + "." + payload + "." + (signed ? "c2ln" : "");
    }

    @Test
    void test_harness_flags_a_device_below_3fps() {
        List<Long> t = new ArrayList<>();
        long base = System.nanoTime();
        for (int i = 0; i < 8; i++) {
            t.add(base + i * 500_000_000L); // one frame every 500 ms = 2 fps
        }
        CheckResult r = SbiContractChecks.streamRate(t);
        assertEquals(CheckResult.Status.FAIL, r.status());
        assertTrue(r.detail().contains("PDF minimum is 3"));
    }

    @Test
    void test_harness_passes_a_device_above_3fps() {
        List<Long> t = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            t.add(i * 100_000_000L); // 10 fps
        }
        assertEquals(CheckResult.Status.PASS, SbiContractChecks.streamRate(t).status());
    }

    @Test
    void test_harness_flags_unsigned_rcapture() {
        String body = "{\"biometrics\":[{\"data\":\"" + token(false, "FAC\u0000 record") + "\",\"error\":{\"errorCode\":\"0\"}}]}";
        CheckResult r = SbiContractChecks.rCapture(body.getBytes(StandardCharsets.UTF_8));
        assertEquals(CheckResult.Status.FAIL, r.status());
        assertTrue(r.detail().startsWith("UNSIGNED"));
    }

    @Test
    void test_harness_flags_a_record_without_the_iso_magic() {
        String body = "{\"biometrics\":[{\"data\":\"" + token(true, "JUNK") + "\",\"error\":{\"errorCode\":\"0\"}}]}";
        assertEquals(CheckResult.Status.FAIL, SbiContractChecks.rCapture(body.getBytes(StandardCharsets.UTF_8)).status());
    }

    @Test
    void test_harness_accepts_a_signed_iso_record() {
        String body = "{\"biometrics\":[{\"data\":\"" + token(true, "FAC\u0000 record") + "\",\"error\":{\"errorCode\":\"0\"}}]}";
        assertEquals(CheckResult.Status.PASS, SbiContractChecks.rCapture(body.getBytes(StandardCharsets.UTF_8)).status());
        assertEquals(CheckResult.Status.PASS, SbiContractChecks.digitalIdFields(body.getBytes(StandardCharsets.UTF_8)).status());
    }

    @Test
    void test_live_run_against_a_fake_slow_device_flags_the_stream() throws Exception {
        try (ServerSocket dev = fakeDevice(2.0, token(false, "FAC\u0000 x"))) {
            List<CheckResult> rs = SbiContractChecks.runLive("127.0.0.1", dev.getLocalPort(), 4);
            CheckResult stream = rs.stream().filter(r -> r.name().startsWith("stream")).findFirst().get();
            CheckResult cap = rs.stream().filter(r -> r.name().startsWith("rCapture")).findFirst().get();
            assertEquals(CheckResult.Status.FAIL, stream.status(), stream.detail());
            assertEquals(CheckResult.Status.FAIL, cap.status(), cap.detail());
        }
    }
}
