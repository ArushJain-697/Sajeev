// #60 (WS4) | Tests named as claims. Pure computation: no models, no clips.
package io.sajeev.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetricsTest {

    private static Presentation p(String file, String species, String subject, String device, boolean accepted, String v1) {
        Map<String, String> l = new HashMap<>();
        Map<String, Double> pr = new HashMap<>();
        l.put("V1_texture_minifasnet", v1);
        pr.put("V1_texture_minifasnet", "LIVE".equals(v1) ? 0.95 : 0.02);
        for (String v : new String[] {"V2_moire", "V4_frame_edge"}) {
            l.put(v, "LIVE");
            pr.put(v, 0.9);
        }
        l.put("V3_flatness", "ABSTAIN");
        pr.put("V3_flatness", 0.0);
        l.put("V5_reflection", "ABSTAIN");
        pr.put("V5_reflection", 0.0);
        return new Presentation(file, species, subject, device, accepted, accepted ? "LIVE_PASSIVE" : "ATTACK_SUSPECTED", l, pr, false);
    }

    @Test
    void test_wilson_interval_matches_reference_values() {
        assertEquals(0.2038, Wilson.interval(0, 15)[1], 0.001); // n=15, 0 events: upper about 0.20
        assertEquals(0.0876, Wilson.interval(0, 40)[1], 0.001); // n=40: about 0.088
        assertEquals(0.0, Wilson.interval(0, 15)[0], 1e-12);
        assertEquals(0.0, Wilson.interval(0, 0)[0], 0.0);
        assertEquals(1.0, Wilson.interval(0, 0)[1], 0.0);
        double[] w = Wilson.interval(5, 10); // symmetric around 0.5
        assertEquals(1.0, w[0] + w[1], 1e-9);
    }

    private static List<Presentation> sample() {
        List<Presentation> ps = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ps.add(p("b" + i, "bona", "s" + (i % 2), "dA", i != 3, i != 3 ? "LIVE" : "ATTACK")); // 1 of 10 bona fide rejected
        }
        for (int i = 0; i < 8; i++) {
            ps.add(p("p1_" + i, "P1", "s0", "dA", i == 0, i == 0 ? "LIVE" : "ATTACK")); // 1 of 8 print attacks accepted
        }
        for (int i = 0; i < 6; i++) {
            ps.add(p("p2_" + i, "P2", "s1", "dB", false, "ATTACK"));
        }
        return ps;
    }

    @Test
    void test_rates_count_events_per_presentation() {
        String json = Metrics.toJson(sample(), 0.80, 3);
        assertTrue(json.contains("\"P1\":{\"n\":8,\"events\":1,\"rate\":0.1250"), json);
        assertTrue(json.contains("\"P2\":{\"n\":6,\"events\":0,\"rate\":0.0000"), json);
        assertTrue(json.contains("\"max_apcer\":0.1250"), json);
        assertTrue(json.contains("\"bpcer\":{\"n\":10,\"events\":1,\"rate\":0.1000"), json);
    }

    @Test
    void test_b0_no_liveness_is_100_percent_apcer_by_construction() {
        String json = Metrics.toJson(sample(), 0.80, 3);
        int at = json.indexOf("\"B0_no_liveness\"");
        assertTrue(json.indexOf("\"max_apcer\":1.0000", at) > 0, json);
        assertTrue(json.contains("100% by construction"));
    }

    @Test
    void test_metrics_json_is_reproducible_byte_for_byte() {
        List<Presentation> a = sample();
        List<Presentation> b = sample();
        Collections.reverse(b); // input order must not matter for the numbers
        assertEquals(Metrics.toJson(a, 0.80, 3), Metrics.toJson(a, 0.80, 3));
        // the per-species and per-subject blocks are sorted, so reversed input gives the same bytes except the list of failures
        assertEquals(Metrics.toJson(a, 0.80, 3), Metrics.toJson(b, 0.80, 3));
    }

    @Test
    void test_breakdowns_by_subject_and_device_exist() {
        String json = Metrics.toJson(sample(), 0.80, 3);
        assertTrue(json.contains("\"by_subject\":{\"s0\""), json);
        assertTrue(json.contains("\"by_device\":{\"dA\""), json);
    }

    @Test
    void test_ablation_of_a_voter_changes_the_passive_decision() {
        Presentation attack = p("x", "P1", "s0", "dA", false, "ATTACK");
        assertTrue(!Metrics.passiveAccept(attack, null, 0.80, 3)); // V1 says ATTACK: one attack means no passive pass
        assertTrue(Metrics.passiveAccept(attack, "V1_texture_minifasnet", 0.65, 2)); // without V1 the rest look live (two abstentions count 0.5)
    }
}
