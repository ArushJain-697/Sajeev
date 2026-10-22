// #31 (WS1) | Bible §8 | Mutation harness: switch each safety rule off, count the false accepts. Java 11 only.
// A false accept is an ACCEPT that the full rule set would have rejected. The count must rise above zero for every rule.
package io.sajeev.session;

import io.sajeev.active.ChallengeReevaluator;
import io.sajeev.active.ChallengeSession;
import io.sajeev.api.Vote;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutationTest {

    private static final int CASES = 20000;

    private static List<Scenario> scenarios(long seed) {
        Random r = new Random(seed);
        List<Scenario> out = new ArrayList<>();
        Scenario.Ev[] evs = Scenario.Ev.values();
        for (int n = 0; n < CASES; n++) {
            List<Vote> votes = new ArrayList<>();
            boolean honest = r.nextInt(3) > 0; // two thirds of the cases look like a live face to the voters
            for (int i = 0; i < 5; i++) {
                int k = r.nextInt(10);
                if (honest) {
                    votes.add(k < 7 ? new Vote(Vote.Label.LIVE, 0.85 + 0.15 * r.nextDouble(), "t")
                            : k < 9 ? new Vote(Vote.Label.ABSTAIN, 0, "t") : new Vote(Vote.Label.ATTACK, 0.2, "t"));
                } else {
                    votes.add(k < 4 ? new Vote(Vote.Label.ABSTAIN, 0, "t")
                            : k < 7 ? new Vote(Vote.Label.LIVE, 0.5 + 0.5 * r.nextDouble(), "t")
                            : new Vote(Vote.Label.ATTACK, 0.1, "t"));
                }
            }
            List<Scenario.Ev> ev = new ArrayList<>();
            int len = 4 + r.nextInt(10);
            for (int i = 0; i < len; i++) {
                ev.add(evs[r.nextInt(evs.length)]);
            }
            if (r.nextBoolean()) { // half the time: observations, then a verdict, then the capture (the normal order)
                ev.clear();
                ev.add(Scenario.Ev.OBSERVE_HONEST);
                ev.add(Scenario.Ev.OBSERVE_HONEST);
                ev.add(Scenario.Ev.VERDICT);
                ev.add(Scenario.Ev.CAPTURE);
            }
            ChallengeReevaluator.Finish[] fin = ChallengeReevaluator.Finish.values();
            out.add(new Scenario(votes, r.nextInt(10) > 0,
                    r.nextBoolean() ? ChallengeSession.Outcome.ACTIVE_PASS : ChallengeSession.Outcome.CHALLENGE_TIMEOUT,
                    fin[r.nextInt(fin.length)], ev, r.nextInt(4) == 0, r.nextInt(8) == 0 ? 2 : 1, r.nextInt(9000)));
        }
        return out;
    }

    private static int falseAccepts(List<Scenario> all, Safeguards mutated) {
        int count = 0;
        for (Scenario sc : all) {
            boolean full = sc.result(Safeguards.allOn()) == SessionAcceptance.Final.ACCEPT;
            boolean mut = sc.result(mutated) == SessionAcceptance.Final.ACCEPT;
            if (mut && !full) {
                count++;
            }
        }
        return count;
    }

    @Test
    void test_mutation_removing_binding_accepts_the_swap() {
        List<Scenario> all = scenarios(31L);
        StringBuilder table = new StringBuilder("| Rule switched off | False accepts out of " + CASES + " |\n|---|---|\n");
        int baseline = falseAccepts(all, Safeguards.allOn());
        assertEquals(0, baseline, "the full rule set must have no false accepts relative to itself");
        for (Safeguards.Rule rule : Safeguards.Rule.values()) {
            int n = falseAccepts(all, Safeguards.allOn().without(rule));
            table.append("| ").append(rule).append(" | ").append(n).append(" |\n");
            assertTrue(n > 0, "switching off " + rule + " must let false accepts through, got " + n);
        }
        // The swap case by name: a capture of a different face is accepted only when Binding is off.
        int swapAccepted = 0;
        for (Scenario sc : all) {
            if (sc.captureIsSwap && sc.result(Safeguards.allOn().without(Safeguards.Rule.BINDING))
                    == SessionAcceptance.Final.ACCEPT) {
                swapAccepted++;
                assertEquals(SessionAcceptance.Final.REJECT, sc.result(Safeguards.allOn()));
            }
        }
        assertTrue(swapAccepted > 0);
        try {
            Path out = Paths.get("target", "mutation-table.md");
            Files.createDirectories(out.getParent());
            Files.write(out, table.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
