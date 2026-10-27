// #23 (WS1) | Bible §6.5 | Tests named as claims. Votes are constructed, not measured: the gate is tested on its own.
package io.sajeev.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sajeev.api.Vote;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DecisionGateTest {

    private static final double THR = 0.80;
    private static final int MIN_LIVE = 3;
    private static final double[] W5 = {1, 1, 1, 1, 1};

    private static Vote live(double p) {
        return new Vote(Vote.Label.LIVE, p, "t");
    }

    private static Vote attack() {
        return new Vote(Vote.Label.ATTACK, 0.05, "t");
    }

    private static Vote abstain() {
        return new Vote(Vote.Label.ABSTAIN, 0.0, "t");
    }

    private static DecisionGate.Decision run(List<Vote> v, boolean active) {
        return DecisionGate.decide(v, Arrays.copyOf(W5, v.size()), true, THR, MIN_LIVE, active);
    }

    @Test
    void test_abstain_never_counts_as_live() {
        // Two LIVE and three ABSTAIN: only two live votes, below minLiveVotes 3, so no passive pass
        List<Vote> v = Arrays.asList(live(0.95), live(0.95), abstain(), abstain(), abstain());
        assertFalse(run(v, true).outcome() == DecisionGate.Outcome.PASSIVE_PASS);
    }

    @Test
    void test_two_attack_votes_always_pad_fail() {
        List<Vote> v = Arrays.asList(attack(), attack(), live(0.99), live(0.99), live(0.99));
        assertEquals(DecisionGate.Outcome.PAD_FAIL, run(v, true).outcome());
    }

    @Test
    void test_exactly_one_attack_requires_strict_active() {
        List<Vote> v = Arrays.asList(attack(), live(0.99), live(0.99), live(0.99), live(0.99));
        DecisionGate.Decision d = run(v, true);
        assertEquals(DecisionGate.Outcome.ACTIVE_REQUIRED, d.outcome());
        assertTrue(d.strict());
    }

    @Test
    void test_all_abstain_requires_active_never_pass() {
        List<Vote> v = Arrays.asList(abstain(), abstain(), abstain(), abstain(), abstain());
        DecisionGate.Decision d = run(v, true);
        assertEquals(DecisionGate.Outcome.ACTIVE_REQUIRED, d.outcome());
        assertEquals(0.0, d.passiveScore());
    }

    @Test
    void test_active_disabled_in_config_blocks_instead_of_passing() {
        List<Vote> v = Arrays.asList(live(0.6), live(0.6), live(0.6), abstain(), abstain());
        assertEquals(DecisionGate.Outcome.LIVENESS_NOT_ESTABLISHED, run(v, false).outcome());
    }

    @Test
    void test_gate_table_243_vote_combinations_x_score_bands() {
        // 3^5 = 243 vote combinations (LIVE, ATTACK, ABSTAIN per voter), each at four live-probability bands.
        Vote.Label[] labels = {Vote.Label.LIVE, Vote.Label.ATTACK, Vote.Label.ABSTAIN};
        double[] bands = {0.10, 0.50, 0.85, 0.99};
        int cases = 0;
        for (int code = 0; code < 243; code++) {
            for (double band : bands) {
                List<Vote> v = new ArrayList<>();
                int c = code;
                for (int i = 0; i < 5; i++) {
                    Vote.Label l = labels[c % 3];
                    c /= 3;
                    v.add(l == Vote.Label.ATTACK ? attack()
                            : l == Vote.Label.ABSTAIN ? abstain() : live(band));
                }
                DecisionGate.Decision d = run(v, true);
                cases++;
                long attacks = v.stream().filter(x -> x.label() == Vote.Label.ATTACK).count();
                long lives = v.stream().filter(x -> x.label() == Vote.Label.LIVE).count();
                // Invariants: a pass needs zero attacks, enough live votes and a score at the threshold
                if (d.outcome() == DecisionGate.Outcome.PASSIVE_PASS) {
                    assertEquals(0, attacks);
                    assertTrue(lives >= MIN_LIVE);
                    assertTrue(d.passiveScore() >= THR);
                }
                if (attacks >= 2) {
                    assertEquals(DecisionGate.Outcome.PAD_FAIL, d.outcome());
                }
                if (lives + attacks == 0) {
                    assertFalse(d.outcome() == DecisionGate.Outcome.PASSIVE_PASS);
                }
            }
        }
        assertEquals(243 * 4, cases);
    }

    // ---- positive-evidence rule (red team, 27 Oct 2026) ----

    /** Voters in engine order: V1 texture, V2 moire, V3 flatness, V4 frame edge, V5 reflection. Only V1 and V3 are evidence of life. */
    private static final boolean[] EVIDENCE = {true, false, true, false, false};

    private static DecisionGate.Decision runEvidence(List<Vote> v, int minLive) {
        return DecisionGate.decide(v, Arrays.copyOf(W5, v.size()), EVIDENCE, true, THR, minLive, true, true);
    }

    @Test
    void test_voters_that_only_find_no_attack_never_count_towards_the_live_minimum() {
        // the phone-screen clip: texture LIVE (a camera that cannot tell), flatness ABSTAIN (nothing moves), the three
        // attack-only voters LIVE because they found nothing. Four LIVE votes, one of them evidence of life.
        List<Vote> v = Arrays.asList(live(1.0), live(0.9), abstain(), live(0.9), live(0.9));
        DecisionGate.Decision d = runEvidence(v, 2);
        assertEquals(DecisionGate.Outcome.ACTIVE_REQUIRED, d.outcome());
    }

    @Test
    void test_a_passive_pass_needs_enough_votes_that_are_evidence_of_life() {
        List<Vote> both = Arrays.asList(live(1.0), live(0.9), live(0.95), live(0.9), live(0.9));
        assertEquals(DecisionGate.Outcome.PASSIVE_PASS, runEvidence(both, 2).outcome());
        List<Vote> onlyTexture = Arrays.asList(live(1.0), live(0.9), abstain(), live(0.9), live(0.9));
        assertFalse(runEvidence(onlyTexture, 2).outcome() == DecisionGate.Outcome.PASSIVE_PASS);
        List<Vote> onlyFlatness = Arrays.asList(abstain(), live(0.9), live(0.95), live(0.9), live(0.9));
        assertFalse(runEvidence(onlyFlatness, 2).outcome() == DecisionGate.Outcome.PASSIVE_PASS);
    }

    @Test
    void test_the_evidence_flags_never_weaken_the_attack_veto() {
        List<Vote> v = Arrays.asList(live(1.0), attack(), live(0.95), attack(), live(0.9));
        assertEquals(DecisionGate.Outcome.PAD_FAIL, runEvidence(v, 2).outcome());
    }

    @Test
    void test_a_flag_list_of_the_wrong_length_is_refused() {
        List<Vote> v = Arrays.asList(live(1.0), live(1.0));
        try {
            DecisionGate.decide(v, new double[] {1, 1}, new boolean[] {true}, true, THR, 1, true, true);
            org.junit.jupiter.api.Assertions.fail("a mismatched flag list was accepted");
        } catch (IllegalArgumentException expected) {
            // fail closed
        }
    }
}
