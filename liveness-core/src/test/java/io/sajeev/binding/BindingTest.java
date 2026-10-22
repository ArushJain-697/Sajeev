// #27 (WS1) | Tests named as claims. SCRIPTED embeddings and geometry: the decision logic is tested on its own.
package io.sajeev.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BindingTest {

    private static final double[] GEOM = {0.30, 0.55, 0.78};
    private static final float[] LIVE = {1, 0, 0, 0};

    private static Binding binding() {
        Binding b = new Binding(0.363, 0.08, 2000, 3);
        b.observeLive(LIVE, GEOM, 0.9);
        b.liveVerdictAt(0L);
        return b;
    }

    private static final long SECOND = 1_000_000_000L;

    @Test
    void test_same_person_is_bound() {
        float[] capture = {0.95f, 0.1f, 0, 0}; // cosine about 0.99
        Binding.Result r = binding().bind(1, capture, new double[] {0.31, 0.55, 0.77}, 3 * SECOND, 5000);
        assertEquals(Binding.Verdict.BOUND, r.verdict(), "failed: " + r.failedChecks());
    }

    @Test
    void test_live_then_swap_is_rejected() {
        float[] other = {0, 1, 0, 0}; // a different face: cosine 0
        Binding.Result r = binding().bind(1, other, new double[] {0.40, 0.65, 0.70}, 3 * SECOND, 5000);
        assertEquals(Binding.Verdict.CAPTURE_UNBOUND, r.verdict());
        assertTrue(r.failedChecks().containsAll(Arrays.asList("embedding", "geometry")));
    }

    @Test
    void test_capture_after_window_is_rejected() {
        // capture timeout 5 s + margin 2 s = 7 s; the capture arrives at 8 s
        Binding.Result r = binding().bind(1, LIVE, GEOM, 8 * SECOND, 5000);
        assertEquals(Binding.Verdict.CAPTURE_UNBOUND, r.verdict());
        assertEquals(Arrays.asList("timing"), r.failedChecks());
    }

    @Test
    void test_two_faces_in_capture_is_rejected() {
        Binding.Result r = binding().bind(2, LIVE, GEOM, 3 * SECOND, 5000);
        assertEquals(Binding.Verdict.CAPTURE_UNBOUND, r.verdict());
        assertTrue(r.failedChecks().contains("faces"));
    }

    @Test
    void test_geometry_signal_rejects_when_embedding_is_unreliable_and_face_differs() {
        // the embedding model is fooled (cosine 0.9) but the proportions are 25% off: still rejected
        float[] fooled = {0.9f, 0.43f, 0, 0};
        Binding.Result r = binding().bind(1, fooled, new double[] {0.30 * 1.25, 0.55, 0.78}, 3 * SECOND, 5000);
        assertEquals(Binding.Verdict.CAPTURE_UNBOUND, r.verdict());
        assertEquals(Arrays.asList("geometry"), r.failedChecks());
    }

    @Test
    void test_no_live_evidence_means_unbound_never_bound() {
        Binding empty = new Binding(0.363, 0.08, 2000, 3);
        empty.liveVerdictAt(0L);
        Binding.Result r = empty.bind(1, LIVE, GEOM, 3 * SECOND, 5000);
        assertEquals(Binding.Verdict.CAPTURE_UNBOUND, r.verdict());
        assertTrue(r.failedChecks().contains("no_live_evidence"));
    }

    @Test
    void test_no_live_verdict_time_means_unbound() {
        Binding b = new Binding(0.363, 0.08, 2000, 3);
        b.observeLive(LIVE, GEOM, 0.9);
        assertTrue(b.bind(1, LIVE, GEOM, 3 * SECOND, 5000).failedChecks().contains("timing"));
    }

    @Test
    void test_ring_keeps_only_the_best_observations() {
        Binding b = new Binding(0.363, 0.08, 2000, 2);
        b.observeLive(new float[] {0, 1, 0, 0}, GEOM, 0.1); // poor quality, dropped when better ones arrive
        b.observeLive(LIVE, GEOM, 0.9);
        b.observeLive(LIVE, GEOM, 0.8);
        b.liveVerdictAt(0L);
        // the poor observation (which would match the "other" face) was dropped, so "other" is rejected
        assertEquals(Binding.Verdict.CAPTURE_UNBOUND, b.bind(1, new float[] {0, 1, 0, 0}, GEOM, SECOND, 5000).verdict());
    }
}
