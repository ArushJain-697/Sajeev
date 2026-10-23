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
}
