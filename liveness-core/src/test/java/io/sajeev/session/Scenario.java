// #31 (WS1) | Bible §8 | One generated attempt: votes, challenge result, and a Binding event order. Test code only.
package io.sajeev.session;

import io.sajeev.active.ChallengeReevaluator;
import io.sajeev.active.ChallengeSession;
import io.sajeev.api.Vote;
import io.sajeev.binding.Binding;
import io.sajeev.gate.DecisionGate;

import java.util.ArrayList;
import java.util.List;

/** Immutable description of an attempt plus the code that runs it through the real gate, Binding and acceptance rule. */
final class Scenario {

    enum Ev { OBSERVE_HONEST, VERDICT, CAPTURE, LATE_FRAME }

    static final double[] WEIGHTS = {0.30, 0.20, 0.15, 0.15, 0.20};
    static final double THRESHOLD = 0.80;
    static final int MIN_LIVE = 3;

    final List<Vote> votes;
    final boolean streamUsable;
    final ChallengeSession.Outcome active;
    final ChallengeReevaluator.Finish reeval;
    final List<Ev> events;
    final boolean captureIsSwap; // capture shows a different face than the live one
    final int captureFaces;
    final long captureDelayMillis;

    Scenario(List<Vote> votes, boolean streamUsable, ChallengeSession.Outcome active,
             ChallengeReevaluator.Finish reeval, List<Ev> events, boolean captureIsSwap, int captureFaces,
             long captureDelayMillis) {
        this.votes = votes;
        this.streamUsable = streamUsable;
        this.active = active;
        this.reeval = reeval;
        this.events = events;
        this.captureIsSwap = captureIsSwap;
        this.captureFaces = captureFaces;
        this.captureDelayMillis = captureDelayMillis;
    }

    static final float[] LIVE_EMB = unit(16, 1);
    static final float[] OTHER_EMB = unit(16, 7);
    static final double[] LIVE_GEOM = {0.42, 0.31, 0.77, 0.55};
    static final double[] OTHER_GEOM = {0.60, 0.20, 0.95, 0.30};

    private static float[] unit(int n, int seed) {
        float[] v = new float[n];
        java.util.Random r = new java.util.Random(seed);
        double s = 0;
        for (int i = 0; i < n; i++) {
            v[i] = (float) r.nextGaussian();
            s += v[i] * v[i];
        }
        for (int i = 0; i < n; i++) {
            v[i] /= (float) Math.sqrt(s);
        }
        return v;
    }

    /** What happened at the capture: the Binding result, or null when no capture event occurred. */
    static final class Run {
        Binding.Result binding;
        boolean verdictBeforeCapture;
        boolean captured;
        DecisionGate.Decision gate;
    }

    Run run(Safeguards s, int keepObservations) {
        Run r = new Run();
        Binding b = new Binding(0.363, 0.08, 2000, 5, s.timingCheck());
        long t = 0;
        int observed = 0;
        boolean verdict = false;
        for (Ev e : events) {
            t += 100_000_000L; // 100 ms between events
            switch (e) {
                case OBSERVE_HONEST:
                    if (!r.captured && observed < keepObservations) {
                        b.observeLive(LIVE_EMB, LIVE_GEOM, 0.9);
                        observed++;
                    }
                    break;
                case VERDICT:
                    if (!r.captured) {
                        b.liveVerdictAt(t);
                        verdict = true; // duplicates simply re-stamp
                    }
                    break;
                case LATE_FRAME:
                    break; // a frame after the capture changes nothing
                case CAPTURE:
                    if (!r.captured) {
                        r.captured = true;
                        r.verdictBeforeCapture = verdict;
                        long arrival = t + captureDelayMillis * 1_000_000L;
                        r.binding = b.bind(captureFaces, captureIsSwap ? OTHER_EMB : LIVE_EMB,
                                captureIsSwap ? OTHER_GEOM : LIVE_GEOM, arrival, 3000);
                    }
                    break;
                default:
                    break;
            }
        }
        r.gate = DecisionGate.decide(votes, WEIGHTS, streamUsable, THRESHOLD, MIN_LIVE, true, s.abstainRule());
        return r;
    }

    SessionAcceptance.Final result(Safeguards s) {
        Run r = run(s, Integer.MAX_VALUE);
        return SessionAcceptance.decide(r.gate, active, reeval, r.binding, s);
    }

    Scenario withVotes(List<Vote> v) {
        return new Scenario(v, streamUsable, active, reeval, events, captureIsSwap, captureFaces, captureDelayMillis);
    }

    Scenario copyList() {
        return withVotes(new ArrayList<>(votes));
    }
}
