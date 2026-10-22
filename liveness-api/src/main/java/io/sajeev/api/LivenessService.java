// #13 (WS1) | Bible §6, §9 | Java 11 only. Frozen API: changes need a BUILD_LOG.md note.
package io.sajeev.api;

/**
 * The public engine facade. Registration clients call this; they never call the voters directly (PDF IN1).
 */
public interface LivenessService {

    /**
     * Start a liveness check.
     * @param workflow RESIDENT, OPERATOR or SUPERVISOR
     * @param policy the policy for this workflow
     * @return a new single-use session
     */
    LivenessSession start(Workflow workflow, PolicyConfig policy);
}
