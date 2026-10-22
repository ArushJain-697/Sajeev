// #31 (WS1) | Bible §8 | Property tests for the three safety invariants. jqwik, 10,000 cases each. Java 11 only.
package io.sajeev.session;

import io.sajeev.active.ChallengeReevaluator;
import io.sajeev.active.ChallengeSession;
import io.sajeev.api.Vote;
import io.sajeev.gate.DecisionGate;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvariantsTest {

    // A coherent vote: LIVE has probability >= 0.5, ATTACK < 0.5, as the real voters produce.
    @Provide
    Arbitrary<Vote> vote() {
        return Arbitraries.of(Vote.Label.values()).flatMap(l -> {
            switch (l) {
                case LIVE:
                    return Arbitraries.doubles().between(0.5, 1.0).map(p -> new Vote(l, p, "t"));
                case ATTACK:
                    return Arbitraries.doubles().between(0.0, 0.49).map(p -> new Vote(l, p, "t"));
                default:
                    return Arbitraries.just(new Vote(l, 0.0, "t"));
            }
        });
    }

    @Provide
    Arbitrary<Scenario> scenario() {
        Arbitrary<List<Vote>> votes = vote().list().ofSize(5);
        Arbitrary<List<Scenario.Ev>> events = Arbitraries.of(Scenario.Ev.values()).list().ofMinSize(0).ofMaxSize(14);
        return Combinators.combine(votes, Arbitraries.of(true, true, true, false),
                        Arbitraries.of(ChallengeSession.Outcome.ACTIVE_PASS, ChallengeSession.Outcome.CHALLENGE_TIMEOUT),
                        Arbitraries.of(ChallengeReevaluator.Finish.values()), events)
                .as((v, su, a, re, ev) -> new Object[] {v, su, a, re, ev})
                .flatMap(o -> Combinators.combine(Arbitraries.of(true, false, false), Arbitraries.of(0, 1, 1, 1, 2),
                                Arbitraries.longs().between(0, 9000))
                        .as((swap, faces, delay) -> {
                            @SuppressWarnings("unchecked")
                            List<Vote> v = (List<Vote>) o[0];
                            @SuppressWarnings("unchecked")
                            List<Scenario.Ev> ev = (List<Scenario.Ev>) o[4];
                            return new Scenario(v, (Boolean) o[1], (ChallengeSession.Outcome) o[2],
                                    (ChallengeReevaluator.Finish) o[3], ev, swap, faces, delay);
                        }));
    }

    /** A scenario shaped to give an honest ACCEPT: used as the base for I2 degradations. */
    @Provide
    Arbitrary<Scenario> honestish() {
        return scenario();
    }

    // ---- I1 No-Orphan-Capture: ACCEPT only when a live verdict came first and Binding said BOUND ----
    @Property(tries = 10000)
    void test_I1_no_orphan_capture_10000_cases(@ForAll("scenario") Scenario sc) {
        Scenario.Run r = sc.run(Safeguards.allOn(), Integer.MAX_VALUE);
        SessionAcceptance.Final f = SessionAcceptance.decide(r.gate, sc.active, sc.reeval, r.binding, Safeguards.allOn());
        if (f == SessionAcceptance.Final.ACCEPT) {
            assertTrue(r.captured, "accepted with no capture");
            assertNotNull(r.binding);
            assertTrue(r.verdictBeforeCapture, "capture arrived before any live verdict");
            assertEquals(io.sajeev.binding.Binding.Verdict.BOUND, r.binding.verdict());
            assertTrue(!sc.captureIsSwap, "a swapped capture was accepted");
            assertEquals(1, sc.captureFaces, "a capture without exactly one face was accepted");
        }
    }

    private static int rank(SessionAcceptance.Final f) {
        return f == SessionAcceptance.Final.ACCEPT ? 1 : 0;
    }

    // ---- I2 Less-Evidence-Never-Helps: degrading the evidence never turns a reject into an accept ----
    @Property(tries = 10000)
    void test_I2_less_evidence_never_helps(@ForAll("scenario") Scenario sc, @ForAll int which, @ForAll int idx) {
        Safeguards s = Safeguards.allOn();
        SessionAcceptance.Final before = sc.result(s);
        Scenario worse;
        int k = Math.floorMod(which, 6);
        int i = Math.floorMod(idx, sc.votes.size());
        List<Vote> v = new ArrayList<>(sc.votes);
        switch (k) {
            case 0: // a LIVE voter can no longer judge
                if (v.get(i).label() == Vote.Label.LIVE) {
                    v.set(i, new Vote(Vote.Label.ABSTAIN, 0.0, "degraded"));
                }
                worse = sc.withVotes(v);
                break;
            case 1: // the stream becomes unusable
                worse = new Scenario(v, false, sc.active, sc.reeval, sc.events, sc.captureIsSwap, sc.captureFaces,
                        sc.captureDelayMillis);
                break;
            case 2: // the challenge times out
                worse = new Scenario(v, sc.streamUsable, ChallengeSession.Outcome.CHALLENGE_TIMEOUT, sc.reeval,
                        sc.events, sc.captureIsSwap, sc.captureFaces, sc.captureDelayMillis);
                break;
            case 3: // re-evaluation does not clear (PAD_FAIL is already worse than not clearing, so it stays)
                worse = new Scenario(v, sc.streamUsable, sc.active,
                        sc.reeval == ChallengeReevaluator.Finish.OK ? ChallengeReevaluator.Finish.STRICT_NOT_CLEARED : sc.reeval,
                        sc.events, sc.captureIsSwap, sc.captureFaces, sc.captureDelayMillis);
                break;
            case 4: { // fewer live observations kept for Binding (drop all of them)
                SessionAcceptance.Final f = SessionAcceptance.decide(sc.run(s, 0).gate, sc.active, sc.reeval,
                        sc.run(s, 0).binding, s);
                assertTrue(rank(f) <= rank(before), "dropping live observations improved the result");
                return;
            }
            default: // the capture arrives later
                worse = new Scenario(v, sc.streamUsable, sc.active, sc.reeval, sc.events, sc.captureIsSwap,
                        sc.captureFaces, sc.captureDelayMillis + 5000);
        }
        assertTrue(rank(worse.result(s)) <= rank(before), "less evidence turned a reject into an accept (case " + k + ")");
    }

    // ---- I3 Veto-Wins: two ATTACK votes, or an attack found during the challenge, always reject ----
    @Property(tries = 10000)
    void test_I3_veto_wins(@ForAll("scenario") Scenario sc, @ForAll int a, @ForAll int b) {
        Safeguards s = Safeguards.allOn();
        List<Vote> v = new ArrayList<>(sc.votes);
        int i = Math.floorMod(a, v.size());
        int j = Math.floorMod(b + 1 + i, v.size());
        if (i == j) {
            j = (i + 1) % v.size();
        }
        v.set(i, new Vote(Vote.Label.ATTACK, 0.0, "veto"));
        v.set(j, new Vote(Vote.Label.ATTACK, 0.0, "veto"));
        Scenario vetoed = sc.withVotes(v);
        assertEquals(SessionAcceptance.Final.REJECT, vetoed.result(s), "two ATTACK votes did not reject");
        if (sc.streamUsable) { // G1 (cannot judge) comes first when the stream is unusable; the final answer is still REJECT
            assertEquals(DecisionGate.Outcome.PAD_FAIL, vetoed.run(s, Integer.MAX_VALUE).gate.outcome());
        }
        Scenario attackDuringChallenge = new Scenario(sc.votes, sc.streamUsable, sc.active,
                ChallengeReevaluator.Finish.PAD_FAIL, sc.events, sc.captureIsSwap, sc.captureFaces,
                sc.captureDelayMillis);
        assertEquals(SessionAcceptance.Final.REJECT, attackDuringChallenge.result(s),
                "an attack found during the challenge did not reject");
    }

    // Targeted I2 probe: a weak LIVE voter that abstains must not lift the score over the threshold.
    @org.junit.jupiter.api.Test
    void i2_abstaining_weak_live_voter_cannot_lift_the_score() {
        List<Vote> with = new ArrayList<>();
        double[] p = {0.5, 0.99, 0.99, 0.99, 0.5};
        for (double x : p) {
            with.add(new Vote(Vote.Label.LIVE, x, "t"));
        }
        DecisionGate.Decision before = DecisionGate.decide(with, Scenario.WEIGHTS, true, Scenario.THRESHOLD,
                Scenario.MIN_LIVE, true);
        List<Vote> less = new ArrayList<>(with);
        less.set(0, new Vote(Vote.Label.ABSTAIN, 0.0, "t"));
        less.set(4, new Vote(Vote.Label.ABSTAIN, 0.0, "t"));
        DecisionGate.Decision after = DecisionGate.decide(less, Scenario.WEIGHTS, true, Scenario.THRESHOLD,
                Scenario.MIN_LIVE, true);
        assertTrue(!(before.outcome() != DecisionGate.Outcome.PASSIVE_PASS
                && after.outcome() == DecisionGate.Outcome.PASSIVE_PASS),
                "abstaining weak voters turned ACTIVE_REQUIRED into PASSIVE_PASS");
    }
}
