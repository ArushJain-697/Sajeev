// #31 (WS1) | Bible §8 | The final accept/reject rule that combines the gate, the challenge, the re-evaluation and Binding.
// ACCEPT needs a live verdict AND a bound capture. A presentation attack anywhere in the session is a veto. Java 11 only.
package io.sajeev.session;

import io.sajeev.active.ChallengeReevaluator;
import io.sajeev.active.ChallengeSession;
import io.sajeev.binding.Binding;
import io.sajeev.gate.DecisionGate;

/** Pure function over the results of the four stages. */
public final class SessionAcceptance {

    /** The end result of one attempt. */
    public enum Final { ACCEPT, REJECT }

    private SessionAcceptance() {
    }

    /**
     * @param gate the gate's decision after the passive stage
     * @param active the challenge outcome, or null when no challenge was run
     * @param reeval the re-evaluation result for the challenge, or null when no challenge was run
     * @param binding Binding's result for the stored capture, or null when no capture arrived
     * @param s the safety rules (all on in production)
     * @return ACCEPT only for a live session whose capture is bound
     */
    public static Final decide(DecisionGate.Decision gate, ChallengeSession.Outcome active,
                               ChallengeReevaluator.Finish reeval, Binding.Result binding, Safeguards s) {
        if (gate.outcome() == DecisionGate.Outcome.PAD_FAIL) {
            return Final.REJECT; // G2: not a switchable rule
        }
        if (s.reevaluation() && reeval == ChallengeReevaluator.Finish.PAD_FAIL) {
            return Final.REJECT;
        }
        boolean live;
        if (gate.outcome() == DecisionGate.Outcome.PASSIVE_PASS) {
            live = true;
        } else if (gate.outcome() == DecisionGate.Outcome.ACTIVE_REQUIRED) {
            live = active == ChallengeSession.Outcome.ACTIVE_PASS
                    && reeval != null
                    && (!s.strictMode() || reeval != ChallengeReevaluator.Finish.STRICT_NOT_CLEARED);
        } else {
            live = false; // CANNOT_JUDGE and LIVENESS_NOT_ESTABLISHED are never accepted
        }
        if (!live) {
            return Final.REJECT;
        }
        if (s.binding() && (binding == null || binding.verdict() != Binding.Verdict.BOUND)) {
            return Final.REJECT; // I1: no orphan capture
        }
        return Final.ACCEPT;
    }
}
