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
}
