// #31 (WS1) | Bible §8 | The safety rules, as switches. All ON in production: no configuration key turns one off.
// Only the mutation harness builds a Safeguards with a rule disabled, to prove that rule is doing work. Java 11 only.
package io.sajeev.session;

/** Which safety rules are active. */
public final class Safeguards {

    /** The rules the mutation harness switches off one at a time. */
    public enum Rule { BINDING, ABSTAIN_RULE, STRICT_MODE, TIMING_CHECK, REEVALUATION }

    private final boolean binding;
    private final boolean abstainRule;
    private final boolean strictMode;
    private final boolean timingCheck;
    private final boolean reevaluation;

    private Safeguards(boolean binding, boolean abstainRule, boolean strictMode, boolean timingCheck, boolean reevaluation) {
        this.binding = binding;
        this.abstainRule = abstainRule;
        this.strictMode = strictMode;
        this.timingCheck = timingCheck;
        this.reevaluation = reevaluation;
    }

    /** @return every rule on (the only value production uses) */
    public static Safeguards allOn() {
        return new Safeguards(true, true, true, true, true);
    }

    /** @param rule the rule to switch off (mutation testing only) @return a copy with that rule off */
    public Safeguards without(Rule rule) {
        return new Safeguards(binding && rule != Rule.BINDING, abstainRule && rule != Rule.ABSTAIN_RULE,
                strictMode && rule != Rule.STRICT_MODE, timingCheck && rule != Rule.TIMING_CHECK,
                reevaluation && rule != Rule.REEVALUATION);
    }

    public boolean binding() { return binding; }
    public boolean abstainRule() { return abstainRule; }
    public boolean strictMode() { return strictMode; }
    public boolean timingCheck() { return timingCheck; }
    public boolean reevaluation() { return reevaluation; }
}
