// Red-team 2 fixes on the real engine session (frozen stream, timestamps, size caps, single-use capture, session limit,
// deterministic feeding). Needs the models (-Dsajeev.models=<dir>). Tests are named as claims.
package io.sajeev.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import io.sajeev.image.ImageCodec;
import io.sajeev.metrics.LivenessMetrics;
import io.sajeev.policy.Config;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EngineRedTeamTest {

    private static final long STEP = 100_000_000L; // 10 fps
    private static final long T0 = 1_000_000_000L;
    private static LivenessEngine engine;
    private static EngineSettings settings;

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
        ImageCodec.install(null);
        ImageCodec.setLimits(ImageCodec.DEFAULT_MAX_SIDE, ImageCodec.DEFAULT_MAX_PIXELS);
        if (engine != null) {
            engine.close();
        }
    }

    private static byte[] face() throws IOException {
        return Files.readAllBytes(Paths.get("../testdata/fixtures-faces/one_face.jpg"));
    }

    private static PolicyConfig policy() {
        return new PolicyConfig(0.80, 2, 3);
    }

    private static boolean live(Verdict v) {
        return v != null && (v.tier() == Verdict.Tier.LIVE_PASSIVE || v.tier() == Verdict.Tier.LIVE_ACTIVE);
    }

    // ------------------------------------------------------------------ 1. frozen stream

    @Test
    void test_a_stream_of_one_repeated_frame_is_a_frozen_stream_and_never_passes() throws Exception {
        EngineSession s = engine.start(Workflow.RESIDENT, policy());
        byte[] jpeg = face();
        for (int i = 0; i < 60 && s.state() != LivenessSession.State.DONE; i++) {
            s.onFrame(new Frame(jpeg, T0 + i * STEP, i));
            assertTrue(s.awaitIdle(20_000));
        }
        assertNotNull(s.verdict());
        assertEquals(Verdict.Tier.ATTACK_SUSPECTED, s.verdict().tier());
        assertEquals(ErrorState.PAD_FAIL, s.verdict().error().get());
        assertEquals("frozen_stream", s.verdict().reason());
    }

    @Test
    void test_a_stream_with_sensor_noise_is_not_called_frozen() throws Exception {
        EngineSession s = engine.start(Workflow.RESIDENT, policy());
        byte[] jpeg = face();
        for (int i = 0; i < 12 && s.state() != LivenessSession.State.DONE; i++) {
            s.onFrame(new Frame(LivenessEngineTest.sensorNoise(jpeg, i), T0 + i * STEP, i));
            assertTrue(s.awaitIdle(20_000));
        }
        assertFalse(s.verdict() != null && "frozen_stream".equals(s.verdict().reason()), "a noisy stream was called frozen");
        s.abort();
    }

    @Test
    void test_no_passive_decision_is_made_before_the_minimum_observation_time() throws Exception {
        EngineSession s = engine.start(Workflow.RESIDENT, policy());
        byte[] jpeg = face();
        // 30 fps: the 8-frame window is full after 0.23 s of frame time, but 0.7 s must pass before the passive stage may decide
        for (int i = 0; i < 18; i++) {
            s.onFrame(new Frame(LivenessEngineTest.sensorNoise(jpeg, i), T0 + i * 33_000_000L, i));
            assertTrue(s.awaitIdle(20_000));
        }
        assertNull(s.verdict(), "decided after " + (17 * 33) + " ms of frame time: " + s.verdict());
        assertTrue(s.passiveVotes().isEmpty(), "the voters ran before the minimum time");
        s.abort();
    }

    // ------------------------------------------------------------------ 2. timestamps

    @Test
    void test_frames_with_repeated_arrival_times_are_ignored_and_end_as_invalid_frame_data() throws Exception {
        EngineSession s = engine.start(Workflow.RESIDENT, policy());
        byte[] jpeg = face();
        for (int i = 0; i < 40 && s.state() != LivenessSession.State.DONE; i++) {
            s.onFrame(new Frame(LivenessEngineTest.sensorNoise(jpeg, i), T0, i)); // the same time for every frame: "infinite fps"
        }
        assertTrue(s.awaitIdle(20_000));
        assertNotNull(s.verdict());
        assertFalse(live(s.verdict()));
        assertEquals(ErrorState.INVALID_FRAME_DATA, s.verdict().error().get());
        assertEquals("invalid_timestamps", s.verdict().reason());
    }

    @Test
    void test_long_max_and_long_min_arrival_times_cannot_defeat_the_minimum_fps_guard() throws Exception {
        EngineSession s = engine.start(Workflow.RESIDENT, policy());
        byte[] jpeg = face();
        for (int i = 0; i < 40 && s.state() != LivenessSession.State.DONE; i++) {
            s.onFrame(new Frame(jpeg, i % 2 == 0 ? Long.MAX_VALUE : Long.MIN_VALUE, i));
        }
        assertTrue(s.awaitIdle(20_000));
        assertNotNull(s.verdict());
        assertFalse(live(s.verdict()));
        assertEquals(ErrorState.INVALID_FRAME_DATA, s.verdict().error().get());
    }

    @Test
    void test_backward_arrival_times_are_refused_and_do_not_move_the_clock() throws Exception {
        EngineSession s = engine.start(Workflow.RESIDENT, policy());
        byte[] jpeg = face();
        s.onFrame(new Frame(LivenessEngineTest.sensorNoise(jpeg, 0), T0 + 5 * STEP, 0));
        assertTrue(s.awaitIdle(20_000));
        s.onFrame(new Frame(LivenessEngineTest.sensorNoise(jpeg, 1), T0, 1)); // earlier than the one before
        assertTrue(s.awaitIdle(20_000));
        assertEquals(ErrorState.INVALID_FRAME_DATA, s.liveGuidance());
        assertNull(s.verdict(), "one bad time is flagged, not fatal");
        s.abort();
    }

    @Test
    void test_an_absurd_forward_jump_in_arrival_time_is_clamped() throws Exception {
        EngineSession s = engine.start(Workflow.RESIDENT, policy());
        byte[] jpeg = face();
        s.onFrame(new Frame(LivenessEngineTest.sensorNoise(jpeg, 0), T0, 0));
        s.onFrame(new Frame(LivenessEngineTest.sensorNoise(jpeg, 1), T0 + 3_600_000_000_000L, 1)); // one hour later
        assertTrue(s.awaitIdle(20_000));
        // Clamped to 10 s after the first frame. Unclamped, the frame would be an hour after the first one and the passive stage
        // would give up at once with "no usable face" (it waits 10 s of frame time, and 10 s is not more than 10 s).
        assertNull(s.verdict(), "the hour-long gap was taken at face value: " + s.verdict());
        s.abort();
    }

    // ------------------------------------------------------------------ 3. size and Errors

    private static byte[] withDeclaredSize(byte[] jpeg, int w, int h) {
        byte[] b = jpeg.clone();
        for (int i = 2; i + 8 < b.length; i++) {
            if ((b[i] & 0xFF) == 0xFF && ((b[i + 1] & 0xFF) == 0xC0 || (b[i + 1] & 0xFF) == 0xC2)) {
                b[i + 5] = (byte) (h >> 8);
                b[i + 6] = (byte) h;
                b[i + 7] = (byte) (w >> 8);
                b[i + 8] = (byte) w;
                return b;
            }
        }
        throw new IllegalStateException("no SOF marker");
    }

    @Test
    void test_an_image_that_declares_more_than_the_size_cap_is_refused_before_any_pixel_is_allocated() throws Exception {
        byte[] huge = withDeclaredSize(face(), 30000, 30000);
        int[] size = ImageCodec.declaredSize(huge);
        assertEquals(30000, size[0]);
        assertTrue(ImageCodec.tooLarge(huge));
        assertNull(ImageCodec.decode(huge));
        assertTrue(ImageCodec.tooLarge(withDeclaredSize(face(), 8193, 100)), "a side over 8192");
        assertTrue(ImageCodec.tooLarge(withDeclaredSize(face(), 8000, 6000)), "48 megapixels");
        assertFalse(ImageCodec.tooLarge(face()));
    }

    @Test
    void test_a_session_fed_huge_declared_frames_ends_without_a_live_verdict_and_does_not_hang() throws Exception {
        EngineSession s = engine.start(Workflow.RESIDENT, policy());
        byte[] huge = withDeclaredSize(face(), 20000, 20000);
        for (int i = 0; i < 150 && s.state() != LivenessSession.State.DONE; i++) {
            s.onFrame(new Frame(huge, T0 + i * STEP, i));
            assertTrue(s.awaitIdle(20_000));
        }
        assertNotNull(s.verdict(), "the session never ended");
        assertFalse(live(s.verdict()));
    }

    @Test
    void test_an_OutOfMemoryError_in_the_worker_ends_the_session_as_ENGINE_ERROR_and_does_not_hang() throws Exception {
        EngineSession s = engine.start(Workflow.RESIDENT, policy());
        ImageCodec.install(b -> {
            throw new OutOfMemoryError("test");
        });
        try {
            s.onFrame(new Frame(face(), T0, 0));
            assertTrue(s.awaitIdle(20_000));
        } finally {
            ImageCodec.install(null);
        }
        assertNotNull(s.verdict());
        assertEquals(Verdict.Tier.CANNOT_JUDGE, s.verdict().tier());
        assertEquals(ErrorState.ENGINE_ERROR, s.verdict().error().get());
        assertTrue(s.verdict().reason().contains("OutOfMemoryError"), s.verdict().reason());
    }

    @Test
    void test_a_StackOverflowError_in_the_worker_also_fails_closed() throws Exception {
        EngineSession s = engine.start(Workflow.RESIDENT, policy());
        ImageCodec.install(b -> {
            throw new StackOverflowError();
        });
        try {
            s.onFrame(new Frame(face(), T0, 0));
            assertTrue(s.awaitIdle(20_000));
        } finally {
            ImageCodec.install(null);
        }
        assertEquals(ErrorState.ENGINE_ERROR, s.verdict().error().get());
    }

    @Test
    void test_checkCapture_with_a_huge_or_failing_capture_returns_unbound_and_never_throws() throws Exception {
        EngineSession s = engine.start(Workflow.RESIDENT, policy());
        s.endForTest(Verdict.Tier.LIVE_PASSIVE, null, "test");
        Binding.Result r = s.checkCapture(withDeclaredSize(face(), 20000, 20000), System.nanoTime(), 3000);
        assertEquals(Binding.Verdict.CAPTURE_UNBOUND, r.verdict());
        EngineSession t = engine.start(Workflow.RESIDENT, policy());
        t.endForTest(Verdict.Tier.LIVE_PASSIVE, null, "test");
        ImageCodec.install(b -> {
            throw new OutOfMemoryError("test");
        });
        try {
            assertEquals(Binding.Verdict.CAPTURE_UNBOUND, t.checkCapture(face(), System.nanoTime(), 3000).verdict());
        } finally {
            ImageCodec.install(null);
        }
    }

    @Test
    void test_the_session_after_the_concurrent_limit_ends_at_once_with_ENGINE_ERROR() throws Exception {
        int saved = settings.maxConcurrentSessions;
        try {
            long end = System.currentTimeMillis() + 10_000;
            while (engine.runningSessions() > 0 && System.currentTimeMillis() < end) {
                Thread.sleep(20); // sessions of earlier tests are still stopping
            }
            settings.maxConcurrentSessions = 3;
            EngineSession a = engine.start(Workflow.RESIDENT, policy());
            EngineSession b = engine.start(Workflow.RESIDENT, policy());
            EngineSession c = engine.start(Workflow.RESIDENT, policy());
            EngineSession d = engine.start(Workflow.RESIDENT, policy());
            assertNull(a.verdict());
            assertNotNull(d.verdict(), "the fourth session was not refused");
            assertEquals(LivenessSession.State.DONE, d.state());
            assertEquals(ErrorState.ENGINE_ERROR, d.verdict().error().get());
            d.onFrame(new Frame(face(), T0, 0)); // frames to a refused session are ignored
            a.abort();
            end = System.currentTimeMillis() + 5000;
            while (engine.runningSessions() > 2 && System.currentTimeMillis() < end) {
                Thread.sleep(20);
            }
            EngineSession e = engine.start(Workflow.RESIDENT, policy());
            assertNull(e.verdict(), "a slot freed by an ended session was not reusable");
            b.abort();
            c.abort();
            e.abort();
        } finally {
            settings.maxConcurrentSessions = saved;
        }
    }

    // ------------------------------------------------------------------ 4. capture is single use

    @Test
    void test_checkCapture_before_a_live_verdict_is_refused_and_does_not_use_up_the_session() throws Exception {
        EngineSession s = engine.start(Workflow.RESIDENT, policy());
        Binding.Result early = s.checkCapture(face(), System.nanoTime(), 3000);
        assertEquals(Binding.Verdict.CAPTURE_UNBOUND, early.verdict());
        assertTrue(early.failedChecks().contains("no_live_verdict"));
        s.abort();
    }

    @Test
    void test_a_second_checkCapture_on_one_live_verdict_is_refused_as_already_used() throws Exception {
        EngineSession s = engine.start(Workflow.RESIDENT, policy());
        s.endForTest(Verdict.Tier.LIVE_PASSIVE, null, "test");
        Binding.Result first = s.checkCapture(face(), System.nanoTime(), 3000);
        Binding.Result second = s.checkCapture(face(), System.nanoTime(), 3000);
        assertTrue(second.failedChecks().contains("already_used"), second.failedChecks().toString());
        assertEquals(Binding.Verdict.CAPTURE_UNBOUND, second.verdict());
        assertEquals(first.verdict(), s.bindingResult().verdict(), "the first result must stay");
    }

    @Test
    void test_a_non_live_verdict_never_binds_a_capture() throws Exception {
        for (Verdict.Tier tier : new Verdict.Tier[] {Verdict.Tier.ATTACK_SUSPECTED, Verdict.Tier.CANNOT_JUDGE,
            Verdict.Tier.ACTIVE_REQUIRED, Verdict.Tier.LIMITED}) {
            EngineSession s = engine.start(Workflow.RESIDENT, policy());
            s.endForTest(tier, null, "test");
            assertTrue(s.checkCapture(face(), System.nanoTime(), 3000).failedChecks().contains("no_live_verdict"), tier.name());
        }
    }

    @Test
    void test_an_aborted_session_cannot_bind_a_capture() throws Exception {
        EngineSession s = engine.start(Workflow.RESIDENT, policy());
        s.abort();
        s.endForTest(Verdict.Tier.LIVE_PASSIVE, null, "late worker"); // a worker that was still finishing must not revive it
        assertEquals(LivenessSession.State.ABORTED, s.state());
        assertTrue(s.checkCapture(face(), System.nanoTime(), 3000).failedChecks().contains("no_live_verdict"));
    }

    // ------------------------------------------------------------------ 8. deterministic feeding

    @Test
    void test_awaitIdle_feeding_drops_no_frame_even_with_a_tiny_queue_and_a_slow_engine() throws Exception {
        int cap = settings.queueCapacity;
        long delay = settings.testDelayMillis;
        settings.queueCapacity = 2;
        settings.testDelayMillis = 40;
        try {
            EngineSession s = engine.start(Workflow.RESIDENT, policy());
            byte[] jpeg = face();
            for (int i = 0; i < 5; i++) { // fewer than the passive window, so the session is still open
                s.onFrame(new Frame(LivenessEngineTest.sensorNoise(jpeg, i), T0 + i * STEP, i));
                assertTrue(s.awaitIdle(20_000));
            }
            io.sajeev.audit.DiagnosticMode on = new io.sajeev.audit.DiagnosticMode(true);
            assertEquals("5", s.diagnostics(on, io.sajeev.audit.DiagnosticMode.Audience.OPERATOR).get("usable face frames"));
            s.abort();
        } finally {
            settings.queueCapacity = cap;
            settings.testDelayMillis = delay;
        }
    }

    @Test
    void test_in_deterministic_mode_the_same_frames_give_the_same_verdict_and_votes_every_time() throws Exception {
        boolean det = settings.deterministic;
        settings.deterministic = true;
        try {
            byte[][] frames = new byte[14][];
            for (int i = 0; i < frames.length; i++) {
                frames[i] = LivenessEngineTest.sensorNoise(face(), i);
            }
            String first = null;
            for (int run = 0; run < 3; run++) {
                EngineSession s = engine.start(Workflow.RESIDENT, policy());
                for (int i = 0; i < frames.length && s.state() != LivenessSession.State.DONE; i++) {
                    s.onFrame(new Frame(frames[i], T0 + i * STEP, i));
                    assertTrue(s.awaitIdle(20_000));
                }
                assertTrue(s.awaitIdle(20_000));
                StringBuilder votes = new StringBuilder();
                s.passiveVotes().forEach((k, v) -> votes.append(k).append('=').append(v.label()).append(':').append(v.probability()).append(' '));
                String now = (s.verdict() == null ? "none" : s.verdict().tier() + "/" + s.verdict().reason()) + " " + votes;
                if (first == null) {
                    first = now;
                }
                assertEquals(first, now, "run " + run + " differs");
                s.abort();
            }
        } finally {
            settings.deterministic = det;
        }
    }
}
