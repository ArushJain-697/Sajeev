// #34 (WS1) | Bible §6, §9 | End-to-end engine tests on still images (no recorded real clips yet: see BUILD_LOG #34).
// Needs the models (-Dsajeev.models=<dir>). Frames are the public-domain fixtures repeated at 10 fps of frame time.
package io.sajeev.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.sajeev.api.ErrorState;
import io.sajeev.api.Frame;
import io.sajeev.api.LivenessSession;
import io.sajeev.api.PolicyConfig;
import io.sajeev.api.Verdict;
import io.sajeev.api.Workflow;
import io.sajeev.audit.AuditLog;
import io.sajeev.binding.Binding;
import io.sajeev.metrics.LivenessMetrics;
import io.sajeev.policy.Config;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LivenessEngineTest {

    private static LivenessEngine engine;
    private static EngineSettings settings;
    private static final long STEP = 100_000_000L; // 10 fps

    @BeforeAll
    static void load() throws Exception {
        String dir = System.getProperty("sajeev.models", System.getenv("SAJEEV_MODELS"));
        assumeTrue(dir != null, "set -Dsajeev.models=<dir>");
        settings = EngineSettings.defaults();
        engine = new LivenessEngine(new EngineModels(Paths.get(dir)), Config.load(new Properties()), settings,
                new AuditLog(System::currentTimeMillis), new LivenessMetrics());
    }

    @AfterAll
    static void close() {
        if (engine != null) {
            engine.close();
        }
    }

    private static byte[] file(String name) throws IOException {
        return Files.readAllBytes(Paths.get("../testdata/fixtures-faces/" + name));
    }

    private static void feed(LivenessSession s, byte[] jpeg, int frames) throws InterruptedException {
        long t = 1_000_000_000L;
        for (int i = 0; i < frames && s.state() != LivenessSession.State.DONE && s.state() != LivenessSession.State.ABORTED; i++) {
            s.onFrame(new Frame(jpeg, t + i * STEP, i));
            Thread.sleep(15); // let the worker keep up like a real camera would
        }
        long end = System.currentTimeMillis() + 30_000;
        while (s.verdict() == null && System.currentTimeMillis() < end) {
            Thread.sleep(50);
        }
    }

    private static String votes(io.sajeev.engine.EngineSession s) {
        StringBuilder b = new StringBuilder();
        s.passiveVotes().forEach((k, v) -> b.append(k).append('=').append(v.label()).append('(').append(v.reason()).append(") "));
        return b.toString();
    }

    private static PolicyConfig policy() {
        return new PolicyConfig(0.80, 2, 3);
    }

    @Test
    void test_a_session_on_a_clip_ends_with_a_verdict_and_a_capture_check() throws Exception {
        io.sajeev.engine.EngineSession s = engine.start(Workflow.RESIDENT, policy());
        byte[] jpeg = file("one_face.jpg");
        feed(s, jpeg, 40);
        assertNotNull(s.verdict(), "no verdict after 40 frames");
        System.out.println("E2E still-photo verdict: " + s.verdict().tier() + " " + s.verdict().error() + " " + s.verdict().reason()
                + " votes=" + votes(s));
        // a still image never passes as a live person without a challenge it cannot perform; whatever the tier, the
        // capture check must not bind unless the verdict is live
        Binding.Result r = s.checkCapture(jpeg, System.nanoTime(), 3000);
        boolean live = s.verdict().tier() == Verdict.Tier.LIVE_PASSIVE || s.verdict().tier() == Verdict.Tier.LIVE_ACTIVE;
        if (!live) {
            assertEquals(Binding.Verdict.CAPTURE_UNBOUND, r.verdict(), "an unbound capture was accepted without a live verdict");
        }
        assertNotNull(s.attemptResult());
    }

    @Test
    void test_no_face_gives_face_not_detected_and_guidance_not_a_pass() throws Exception {
        io.sajeev.engine.EngineSession s = engine.start(Workflow.OPERATOR, policy());
        feed(s, file("zero_faces.jpg"), 150);
        assertEquals(Verdict.Tier.CANNOT_JUDGE, s.verdict().tier());
        assertEquals(ErrorState.FACE_NOT_DETECTED, s.verdict().error().get());
    }

    @Test
    void test_two_faces_give_multiple_faces() throws Exception {
        io.sajeev.engine.EngineSession s = engine.start(Workflow.RESIDENT, policy());
        feed(s, file("two_faces_composite.jpg"), 150);
        assertEquals(Verdict.Tier.CANNOT_JUDGE, s.verdict().tier());
        assertEquals(ErrorState.MULTIPLE_FACES, s.verdict().error().get());
    }

    @Test
    void test_undecodable_frames_give_invalid_frame_data() throws Exception {
        io.sajeev.engine.EngineSession s = engine.start(Workflow.SUPERVISOR, policy());
        feed(s, new byte[] {1, 2, 3, 4, 5, 6, 7, 8}, 150);
        assertEquals(Verdict.Tier.CANNOT_JUDGE, s.verdict().tier());
        assertEquals(ErrorState.INVALID_FRAME_DATA, s.verdict().error().get());
    }

    @Test
    void test_slow_engine_does_not_block_frame_producer() throws Exception {
        int saved = settings.queueCapacity;
        long delay = settings.testDelayMillis;
        settings.testDelayMillis = 300;
        try {
            io.sajeev.engine.EngineSession s = engine.start(Workflow.RESIDENT, policy());
            byte[] jpeg = file("one_face.jpg");
            long t0 = System.nanoTime();
            for (int i = 0; i < 300; i++) {
                s.onFrame(new Frame(jpeg, 1_000_000_000L + i * STEP, i));
            }
            long ms = (System.nanoTime() - t0) / 1_000_000;
            s.abort();
            assertTrue(ms < 1000, "producer was blocked: 300 onFrame calls took " + ms + " ms with a 300 ms engine");
        } finally {
            settings.testDelayMillis = delay;
            settings.queueCapacity = saved;
        }
    }

    @Test
    void test_session_close_releases_all_frames() throws Exception {
        io.sajeev.engine.EngineSession s = engine.start(Workflow.RESIDENT, policy());
        byte[] jpeg = file("one_face.jpg");
        for (int i = 0; i < 6; i++) {
            s.onFrame(new Frame(jpeg, 1_000_000_000L + i * STEP, i));
        }
        Thread.sleep(300);
        s.abort();
        s.abort(); // safe to call twice
        long end = System.currentTimeMillis() + 5000;
        while (!s.released() && System.currentTimeMillis() < end) {
            Thread.sleep(20);
        }
        assertTrue(s.released(), "the worker kept frames after abort");
        assertEquals(LivenessSession.State.ABORTED, s.state());
        s.onFrame(new Frame(jpeg, 9, 99)); // a late frame after close is ignored
    }

    @Test
    void test_engine_makes_no_network_calls() throws IOException {
        // Static check: nothing under liveness-core/src/main refers to java.net, javax.net, HttpClient or sockets.
        // The Docker --network none run named in the ticket is a CI step and was not run here (see BUILD_LOG #34).
        try (Stream<Path> files = Files.walk(Paths.get("src/main/java"))) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                String src = new String(Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8);
                assertFalse(src.contains("java.net") || src.contains("javax.net") || src.contains("HttpClient")
                        || src.contains("java.nio.channels.Socket"), "network class referenced in " + p);
            }
        }
    }

    /**
     * STAND-IN, not a recorded attack: a still photo of a face fed as video is what a printed photo held steady looks like
     * to the engine (no motion, no challenge possible). Real P1-P6 clips come with the recordings (#59, #62).
     */
    @Test
    void test_attack_P1_still_photo_standin_is_rejected_or_failure_is_reported() throws Exception {
        io.sajeev.engine.EngineSession s = engine.start(Workflow.RESIDENT, policy());
        feed(s, file("one_face.jpg"), 60);
        assertNotNull(s.verdict(), "no verdict for a held-still photo");
        assertTrue(s.verdict().tier() != Verdict.Tier.LIVE_PASSIVE && s.verdict().tier() != Verdict.Tier.LIVE_ACTIVE,
                "a still photo was accepted as live: " + s.verdict().tier());
        s.abort();
    }

    @Test
    void test_a_stream_below_the_minimum_fps_is_reported_not_judged() throws Exception {
        io.sajeev.engine.EngineSession s = engine.start(Workflow.RESIDENT, policy());
        byte[] jpeg = file("one_face.jpg");
        long t = 1_000_000_000L;
        for (int i = 0; i < 40 && s.verdict() == null; i++) {
            s.onFrame(new Frame(jpeg, t + i * 500_000_000L, i)); // 2 fps of frame time
            Thread.sleep(40);
        }
        long end = System.currentTimeMillis() + 20000;
        while (s.verdict() == null && System.currentTimeMillis() < end) {
            Thread.sleep(50);
        }
        assertEquals(Verdict.Tier.CANNOT_JUDGE, s.verdict().tier());
        assertEquals(ErrorState.DEVICE_UNAVAILABLE, s.verdict().error().get());
        assertEquals("stream_below_min_fps", s.verdict().reason());
    }

    // ---- STAND-INS for attack species (synthetic edits of the public-domain photo, NOT recorded attacks) ----

    private static byte[] jpegOf(java.awt.image.BufferedImage img) throws IOException {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "jpg", o);
        return o.toByteArray();
    }

    private static java.awt.image.BufferedImage photo() throws IOException {
        return javax.imageio.ImageIO.read(Paths.get("../testdata/fixtures-faces/one_face.jpg").toFile());
    }

    private String run(byte[] jpeg) throws Exception {
        io.sajeev.engine.EngineSession s = engine.start(Workflow.RESIDENT, policy());
        feed(s, jpeg, 130); // long enough for the engine to give up with a guidance error if it never sees a usable face
        assertNotNull(s.verdict());
        String out = s.verdict().tier() + " " + votes(s);
        assertTrue(s.verdict().tier() != Verdict.Tier.LIVE_PASSIVE && s.verdict().tier() != Verdict.Tier.LIVE_ACTIVE,
                "stand-in attack accepted as live: " + out);
        s.abort();
        return out;
    }

    @Test
    void test_attack_P2_screen_standin_moire_overlay_is_rejected_or_failure_is_reported() throws Exception {
        java.awt.image.BufferedImage img = photo();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                double add = 28 * Math.cos(2 * Math.PI * x / 4.0) + 28 * Math.cos(2 * Math.PI * y / 4.0); // pixel-grid beat of a screen
                int r = (int) Math.max(0, Math.min(255, ((rgb >> 16) & 255) + add));
                int g = (int) Math.max(0, Math.min(255, ((rgb >> 8) & 255) + add));
                int b = (int) Math.max(0, Math.min(255, (rgb & 255) + add));
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        System.out.println("P2 stand-in: " + run(jpegOf(img)));
    }

    @Test
    void test_attack_P1_held_print_standin_with_a_dark_border_is_rejected_or_failure_is_reported() throws Exception {
        java.awt.image.BufferedImage src = photo();
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(src.getWidth() + 160, src.getHeight() + 160,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(40, 40, 40));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(src, 80, 80, null); // the photo sits inside a dark frame, as a held-up print or phone does
        g.dispose();
        System.out.println("P1 border stand-in: " + run(jpegOf(img)));
    }

    @Test
    void test_diagnostics_are_visible_only_to_the_operator_when_an_admin_enabled_them_and_hold_no_biometric_data() throws Exception {
        io.sajeev.engine.EngineSession s = engine.start(Workflow.OPERATOR, policy());
        feed(s, file("one_face.jpg"), 40);
        io.sajeev.audit.DiagnosticMode on = new io.sajeev.audit.DiagnosticMode(true);
        io.sajeev.audit.DiagnosticMode off = new io.sajeev.audit.DiagnosticMode(false);
        java.util.Map<String, String> panel = s.diagnostics(on, io.sajeev.audit.DiagnosticMode.Audience.OPERATOR);
        System.out.println("DIAGNOSTIC PANEL: " + panel);
        assertTrue(panel.containsKey("delivered fps") && panel.containsKey("verdict"));
        assertTrue(panel.keySet().stream().anyMatch(k -> k.startsWith("voter V1")), "voter lines are shown");
        assertTrue(s.diagnostics(on, io.sajeev.audit.DiagnosticMode.Audience.RESIDENT).isEmpty());
        assertTrue(s.diagnostics(on, io.sajeev.audit.DiagnosticMode.Audience.SUPERVISOR).isEmpty());
        assertTrue(s.diagnostics(off, io.sajeev.audit.DiagnosticMode.Audience.OPERATOR).isEmpty(), "off by default");
        assertTrue(on.forPacket(panel).isEmpty(), "never in a packet");
        for (String v : panel.values()) {
            assertTrue(v.length() < 120 && !v.matches(".*[A-Za-z0-9+/]{40,}.*"), "a value looks like data, not a status: " + v);
        }
        s.abort();
    }

    @Test
    void test_attack_P3_replay_standin_moving_screen_picture_is_rejected_or_failure_is_reported() throws Exception {
        // A video replay shows a MOVING face on a screen. Stand-in: the photo drifts and zooms a little from frame to frame.
        java.awt.image.BufferedImage src = photo();
        io.sajeev.engine.EngineSession s = engine.start(Workflow.RESIDENT, policy());
        long t = 1_000_000_000L;
        for (int i = 0; i < 60 && s.verdict() == null; i++) {
            int dx = (int) Math.round(12 * Math.sin(i / 4.0));
            double zoom = 1.0 + 0.03 * Math.sin(i / 6.0);
            java.awt.image.BufferedImage f = new java.awt.image.BufferedImage(src.getWidth(), src.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = f.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, dx, 0, (int) (src.getWidth() * zoom), (int) (src.getHeight() * zoom), null);
            g.dispose();
            s.onFrame(new Frame(jpegOf(f), t + i * 100_000_000L, i));
            Thread.sleep(40);
        }
        long end = System.currentTimeMillis() + 30000;
        while (s.verdict() == null && System.currentTimeMillis() < end) {
            Thread.sleep(50);
        }
        assertNotNull(s.verdict());
        System.out.println("P3 stand-in: " + s.verdict().tier() + " " + votes(s));
        assertTrue(s.verdict().tier() != Verdict.Tier.LIVE_PASSIVE && s.verdict().tier() != Verdict.Tier.LIVE_ACTIVE);
        s.abort();
    }

    @Test
    void test_attack_P5_bent_print_standin_is_rejected_or_failure_is_reported() throws Exception {
        // A print bent around a cup: horizontal positions are squeezed by a cylinder mapping, strongest at the sides.
        java.awt.image.BufferedImage src = photo();
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(src.getWidth(), src.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
        double w = src.getWidth();
        double r = w / 2.0 / Math.sin(1.0); // a cylinder spanning +-1 radian
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                double u = (x - w / 2) / (w / 2); // -1..1
                double sx = w / 2 + r * Math.asin(Math.max(-1, Math.min(1, u * Math.sin(1.0)))) ;
                int xs = (int) Math.max(0, Math.min(src.getWidth() - 1, Math.round(sx)));
                img.setRGB(x, y, src.getRGB(xs, y));
            }
        }
        System.out.println("P5 stand-in: " + run(jpegOf(img)));
    }

    @Test
    void test_attack_P6_cutout_standin_eyes_blanked_is_rejected_or_failure_is_reported() throws Exception {
        java.awt.image.BufferedImage img = photo();
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.BLACK);
        g.fillOval(190, 130, 40, 24); // where the eyes are in the photo: a print with the eyes cut out shows what is behind it
        g.fillOval(250, 130, 40, 24);
        g.dispose();
        System.out.println("P6 stand-in: " + run(jpegOf(img)));
    }

    @Test
    void test_attack_P4_swap_standin_a_capture_with_two_faces_is_never_bound() throws Exception {
        // P4 live-then-swap needs a second real identity, which we do not have. What can be checked: after a session, a capture
        // that does not show exactly one face never binds (one real swap case, two people in the capture).
        io.sajeev.engine.EngineSession s = engine.start(Workflow.RESIDENT, policy());
        feed(s, file("one_face.jpg"), 40);
        io.sajeev.binding.Binding.Result r = s.checkCapture(file("two_faces_composite.jpg"), System.nanoTime(), 3000);
        assertEquals(io.sajeev.binding.Binding.Verdict.CAPTURE_UNBOUND, r.verdict());
        assertTrue(r.failedChecks().contains("faces"), r.failedChecks().toString());
        s.abort();
    }
}
