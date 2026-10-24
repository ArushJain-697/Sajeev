// #34 (WS1) | Bible §6.7 | Found while wiring the engine: a zero embedding gives a NaN cosine, which must fail, not pass.
package io.sajeev.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BindingNanTest {
    @Test
    void test_nan_similarity_fails_the_embedding_check() {
        Binding b = new Binding(0.363, 0.08, 2000, 5);
        float[] live = new float[128];
        live[0] = 1f;
        b.observeLive(live, new double[] {0.4, 0.3, 0.8, 0.5}, 1.0);
        b.liveVerdictAt(0);
        Binding.Result r = b.bind(1, new float[128], new double[] {0.4, 0.3, 0.8, 0.5}, 1_000_000L, 3000);
        assertEquals(Binding.Verdict.CAPTURE_UNBOUND, r.verdict());
        assertTrue(r.failedChecks().contains("embedding"), r.failedChecks().toString());
    }

    @Test
    void test_vectors_of_different_length_never_throw_and_never_match() {
        assertEquals(-1.0, SfaceEmbedder.cosine(new float[128], new float[64]), 0.0);
        assertEquals(Double.MAX_VALUE, GeometryRatios.maxRelativeDifference(new double[4], new double[3]), 0.0);
        Binding b = new Binding(0.363, 0.08, 2000, 5);
        b.observeLive(new float[] {1f, 0f}, new double[] {0.4, 0.3, 0.8}, 1.0);
        b.liveVerdictAt(0);
        // a capture whose vectors have the wrong length (what a non-single-face capture used to produce) is refused, not a crash
        Binding.Result r = b.bind(2, new float[128], new double[4], 1_000_000L, 3000);
        assertEquals(Binding.Verdict.CAPTURE_UNBOUND, r.verdict());
    }
}
