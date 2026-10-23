// #61 (WS4) | Tests named as claims. The traces are synthetic; the claim about the chosen value is checked against the committed file.
package io.sajeev.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlinkRecallTest {

    private static List<BlinkRecall.Trace> traces() {
        List<BlinkRecall.Trace> t = new ArrayList<>();
        for (int d : new int[] {100, 150, 200, 250, 300, 400}) {
            t.add(BlinkRecall.synthetic(d));
        }
        return t;
    }

    @Test
    void test_recall_does_not_get_worse_as_the_frame_rate_goes_up_and_is_full_at_30() {
        double[] r = BlinkRecall.recall(traces());
        assertEquals(1.0, r[r.length - 1], 1e-9, "at 30 fps every synthetic blink is seen");
        assertTrue(r[0] <= r[r.length - 1]);
    }

    @Test
    void test_choose_returns_the_lowest_rate_that_stays_above_the_requirement() {
        double[] r = {0.40, 0.70, 0.96, 0.99, 1.0, 1.0};
        assertEquals(8, BlinkRecall.choose(r));
        double[] dip = {0.4, 0.96, 0.9, 0.99, 1.0, 1.0}; // 5 fps is good but 8 fps is not: the rule needs every higher rate to pass
        assertEquals(10, BlinkRecall.choose(dip));
        assertEquals(-1, BlinkRecall.choose(new double[] {0, 0, 0, 0, 0, 0.5}));
    }

    @Test
    void test_blink_minimum_in_config_equals_value_from_experiment() throws Exception {
        int fromExperiment = BlinkRecall.choose(BlinkRecall.recall(traces()));
        String file = new String(Files.readAllBytes(Paths.get("blink/chosen_blink_fps.txt")), StandardCharsets.UTF_8);
        int committed = Integer.parseInt(file.replaceAll("(?s).*CHOSEN_BLINK_MIN_FPS=(\\d+).*", "$1"));
        assertEquals(fromExperiment, committed, "re-run eval/blink/run_blink.sh and commit the new value");
        assertEquals(committed, io.sajeev.engine.EngineSession.BLINK_MIN_FPS, "the engine must use the experiment's value");
    }
}
