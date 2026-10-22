// #33 (WS1) | Tests named as claims
package io.sajeev.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sajeev.model.ExecutionProviders.Evidence;
import io.sajeev.model.ExecutionProviders.Provider;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionProvidersTest {

    private static final List<Provider> AVAILABLE = Arrays.asList(Provider.COREML, Provider.CPU);

    @Test
    void test_accelerator_disabled_when_output_differs() {
        // The Mac CoreML probe differed by 0.00706 from CPU; a tolerance of 1e-4 rejects it (BUILD_LOG #10)
        Evidence coreml = new Evidence(Provider.COREML, 0.00706, 1e-4, "Mac arm64, 22 Oct 2026");
        assertEquals(Provider.CPU, ExecutionProviders.select("AUTO", AVAILABLE, Collections.singletonList(coreml)));
        assertEquals(Provider.CPU, ExecutionProviders.select("COREML", AVAILABLE, Collections.singletonList(coreml)));
    }

    @Test
    void test_auto_picks_cpu_when_no_equivalence_is_recorded() {
        assertEquals(Provider.CPU, ExecutionProviders.select("AUTO", AVAILABLE, Collections.emptyList()));
    }

    @Test
    void test_accelerator_used_only_with_passing_evidence() {
        Evidence ok = new Evidence(Provider.COREML, 5e-5, 1e-4, "device X");
        assertEquals(Provider.COREML, ExecutionProviders.select("AUTO", AVAILABLE, Collections.singletonList(ok)));
    }

    @Test
    void test_cpu_setting_always_wins() {
        Evidence ok = new Evidence(Provider.COREML, 0.0, 1e-4, "device X");
        assertEquals(Provider.CPU, ExecutionProviders.select("CPU", AVAILABLE, Collections.singletonList(ok)));
    }

    @Test
    void test_one_failing_record_disables_the_accelerator() {
        Evidence ok = new Evidence(Provider.COREML, 5e-5, 1e-4, "a");
        Evidence bad = new Evidence(Provider.COREML, 0.01, 1e-4, "b");
        assertFalse(ExecutionProviders.select("AUTO", AVAILABLE, Arrays.asList(ok, bad)) == Provider.COREML);
    }

    @Test
    void test_load_shedding_never_skips_pad_voters() {
        LoadShedder s = new LoadShedder(4);
        for (int depth = 0; depth <= 60; depth += 3) {
            for (double proc : new double[] {5, 30, 90, 400, 5000}) {
                for (boolean cnn : new boolean[] {true, false}) {
                    LoadShedder.Plan p = s.plan(depth, proc, 33.0, cnn);
                    assertTrue(p.padVotersRun(), "PAD voters skipped at depth " + depth + " proc " + proc);
                    assertTrue(p.dropOldest() >= Math.max(0, depth - 4));
                }
            }
        }
    }

    @Test
    void test_overload_drops_oldest_reduces_cnn_then_goes_classical_only_with_a_strict_gate() {
        LoadShedder s = new LoadShedder(4);
        assertEquals(LoadShedder.Level.NORMAL, s.plan(1, 20, 33, true).level());
        assertEquals(LoadShedder.Level.CNN_REDUCED, s.plan(1, 60, 33, true).level());
        LoadShedder.Plan heavy = s.plan(10, 400, 33, true);
        assertEquals(LoadShedder.Level.CLASSICAL_ONLY_STRICT, heavy.level());
        assertTrue(heavy.strictGate());
        assertEquals(6, heavy.dropOldest());
        assertEquals(0, heavy.cnnEveryNth());
        assertTrue(s.plan(0, 5, 33, false).strictGate(), "a missing CNN also means a strict gate, never a silent pass");
    }
}
