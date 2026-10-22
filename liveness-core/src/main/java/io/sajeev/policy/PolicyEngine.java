// #28 (WS1) | Bible §6.8 | Per-workflow policy: hybrid by default, retry counting, what happens after repeated failure,
// what happens when the engine is unavailable. Liveness is never silently skipped. Java 11 only.
package io.sajeev.policy;

import io.sajeev.api.Workflow;

/** Turns configuration into a policy for one workflow, and decides what happens after each attempt. */
public final class PolicyEngine {

    /** How a workflow combines passive and active checks. */
    public enum Mode { HYBRID, ACTIVE_ALWAYS }

    /** What happens after the retry limit (config: onRepeatedFailure). BLOCK is the default. */
    public enum RepeatedFailure { BLOCK, SUPERVISOR_OVERRIDE, EXCEPTION_FLOW }

    /** How one attempt ended, as reported by the gate, the challenge and Binding. */
    public enum AttemptResult {
        /** Liveness passed and the capture is bound. The only result that accepts a face. */
        PASSED,
        /** The gate or the re-evaluation found a presentation attack. */
        PAD_FAIL,
        /** A challenge failed or timed out, or the strict voter did not clear. */
        ACTIVE_FAILED,
        /** Binding rejected the stored capture. */
        UNBOUND,
        /** Passive was not enough and active liveness is disabled: not established, never accepted. */
        NOT_ESTABLISHED,
        /** The engine or a model failed. Never a pass. */
        ENGINE_ERROR,
        /** No usable face or device problem: guidance, and it does not use up a retry. */
        CANNOT_JUDGE
    }

    /** What the client does next. */
    public enum Next {
        /** Accept the face. */
        ACCEPT,
        /** Start a new attempt with a freshly drawn challenge sequence. */
        RETRY,
        /** Stop and guide the person to the recovery process (the default after repeated failure). */
        BLOCK_WITH_RECOVERY,
        /** Opt-in: a supervisor may override. Always audited. */
        SUPERVISOR_OVERRIDE,
        /** Opt-in: MOSIP's biometric-exception flow. Always audited. */
        EXCEPTION_FLOW,
        /** Show guidance and wait for a usable frame; no retry used. */
        GUIDE_AND_WAIT,
        /** Liveness is switched off by configuration for this workflow (audited, never the default). */
        LIVENESS_DISABLED_BY_CONFIG
    }

    /** The decision plus an audit line when the rule requires one. */
    public static final class Decision {
        private final Next next;
        private final String auditEvent;
        private final String guidanceKey;

        Decision(Next next, String auditEvent, String guidanceKey) {
            this.next = next;
            this.auditEvent = auditEvent;
            this.guidanceKey = guidanceKey;
        }

        public Next next() { return next; }
        /** @return an audit line, or null when no audit is required */
        public String auditEvent() { return auditEvent; }
        /** @return the guidance key to show, or null */
        public String guidanceKey() { return guidanceKey; }
    }

    /** The settings for one workflow. */
    public static final class WorkflowPolicy {
        private final Workflow workflow;
        private final boolean livenessEnabled;
        private final Mode mode;
        private final double passiveThreshold;
        private final boolean activeEnabled;
        private final int minChallenges;
        private final int maxRetries;
        private final RepeatedFailure onRepeatedFailure;
        private final String onEngineUnavailable;

        WorkflowPolicy(Workflow workflow, boolean livenessEnabled, Mode mode, double passiveThreshold, boolean activeEnabled,
                       int minChallenges, int maxRetries, RepeatedFailure onRepeatedFailure, String onEngineUnavailable) {
            this.workflow = workflow;
            this.livenessEnabled = livenessEnabled;
            this.mode = mode;
            this.passiveThreshold = passiveThreshold;
            this.activeEnabled = activeEnabled;
            this.minChallenges = minChallenges;
            this.maxRetries = maxRetries;
            this.onRepeatedFailure = onRepeatedFailure;
            this.onEngineUnavailable = onEngineUnavailable;
        }

        public Workflow workflow() { return workflow; }
        public boolean livenessEnabled() { return livenessEnabled; }
        public Mode mode() { return mode; }
        public double passiveThreshold() { return passiveThreshold; }
        public boolean activeEnabled() { return activeEnabled; }
        public int minChallenges() { return minChallenges; }
        public int maxRetries() { return maxRetries; }
        public RepeatedFailure onRepeatedFailure() { return onRepeatedFailure; }
        public String onEngineUnavailable() { return onEngineUnavailable; }
    }

    private final Config config;

    /** @param config the validated liveness configuration */
    public PolicyEngine(Config config) {
        this.config = config;
    }

    /**
     * The policy for a workflow. A per-workflow override in the configuration wins over the global value.
     * Online and offline use the same values: offline uses the last synced configuration (PDF Config).
     * @param workflow RESIDENT, OPERATOR or SUPERVISOR
     * @return the policy
     */
    public WorkflowPolicy policyFor(Workflow workflow) {
        String w = workflow.name().toLowerCase();
        return new WorkflowPolicy(
                workflow,
                Boolean.parseBoolean(config.value("enabled", w)),
                "ACTIVE_ALWAYS".equalsIgnoreCase(config.value("policy.mode", w)) ? Mode.ACTIVE_ALWAYS : Mode.HYBRID,
                Double.parseDouble(config.value("passive.threshold", w)),
                Boolean.parseBoolean(config.value("active.enabled", w)),
                Integer.parseInt(config.value("active.minChallenges", w)),
                Integer.parseInt(config.value("maxRetries", w)),
                RepeatedFailure.valueOf(config.value("onRepeatedFailure", w)),
                config.value("onEngineUnavailable", w));
    }

    /** @param workflow the workflow @return a new session; its retry counter belongs to the session, not the process */
    public WorkflowSession newSession(Workflow workflow) {
        return new WorkflowSession(policyFor(workflow));
    }

    /** One workflow session: counts failed attempts and decides what comes next. */
    public static final class WorkflowSession {
        /** Consecutive "cannot judge" results before they count as a failed attempt (so the wait cannot go on forever). */
        static final int MAX_CANNOT_JUDGE_IN_A_ROW = 10;

        private final WorkflowPolicy policy;
        private int failedAttempts;
        private int cannotJudgeInARow;

        WorkflowSession(WorkflowPolicy policy) {
            this.policy = policy;
        }

        /** @return failed attempts so far in this session */
        public int failedAttempts() {
            return failedAttempts;
        }

        /**
         * @param result how the attempt ended
         * @return what to do next
         */
        public Decision after(AttemptResult result) {
            if (!policy.livenessEnabled()) {
                return new Decision(Next.LIVENESS_DISABLED_BY_CONFIG,
                        "LIVENESS_DISABLED_BY_CONFIG workflow=" + policy.workflow(), null);
            }
            switch (result) {
                case PASSED:
                    return new Decision(Next.ACCEPT, null, "feedback.captured");
                case ENGINE_ERROR:
                    // never a pass: the configured behaviour is BLOCK with guidance
                    return new Decision(Next.BLOCK_WITH_RECOVERY, "ENGINE_UNAVAILABLE workflow=" + policy.workflow(), "error.engine");
                case CANNOT_JUDGE:
                    cannotJudgeInARow++;
                    if (cannotJudgeInARow < MAX_CANNOT_JUDGE_IN_A_ROW) {
                        return new Decision(Next.GUIDE_AND_WAIT, null, "hint.capture_area");
                    }
                    cannotJudgeInARow = 0;
                    return failure();
                default:
                    cannotJudgeInARow = 0;
                    return failure();
            }
        }

        private Decision failure() {
            failedAttempts++;
            if (failedAttempts < policy.maxRetries()) {
                return new Decision(Next.RETRY, null, "failure.liveness");
            }
            String audit = "REPEATED_FAILURE workflow=" + policy.workflow() + " attempts=" + failedAttempts;
            switch (policy.onRepeatedFailure()) {
                case SUPERVISOR_OVERRIDE:
                    return new Decision(Next.SUPERVISOR_OVERRIDE, audit + " action=SUPERVISOR_OVERRIDE", "failure.max_retries");
                case EXCEPTION_FLOW:
                    return new Decision(Next.EXCEPTION_FLOW, audit + " action=EXCEPTION_FLOW", "failure.max_retries");
                default:
                    return new Decision(Next.BLOCK_WITH_RECOVERY, audit + " action=BLOCK", "failure.max_retries");
            }
        }
    }
}
