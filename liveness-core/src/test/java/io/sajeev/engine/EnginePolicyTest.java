// Red-team R1 | Bible §7 | The clients start sessions with the workflow's policy from the configuration, not fixed numbers.
// Needs no models: only the configuration is read.
package io.sajeev.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.sajeev.api.PolicyConfig;
import io.sajeev.api.Workflow;
import io.sajeev.audit.AuditLog;
import io.sajeev.metrics.LivenessMetrics;
import io.sajeev.policy.Config;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class EnginePolicyTest {

    private static LivenessEngine engineWith(Properties p) throws Exception {
        return new LivenessEngine(null, Config.load(p), EngineSettings.defaults(), new AuditLog(System::currentTimeMillis),
                new LivenessMetrics());
    }

    @Test
    void test_defaults_give_the_pdf_policy_for_every_workflow() throws Exception {
        LivenessEngine e = engineWith(new Properties());
        for (Workflow w : Workflow.values()) {
            PolicyConfig p = e.policyFor(w);
            assertEquals(0.80, p.passiveThreshold(), 1e-9);
            assertEquals(2, p.minChallenges());
            assertEquals(3, p.maxRetries());
        }
    }

    @Test
    void test_workflow_override_reaches_the_session_policy() throws Exception {
        Properties p = new Properties();
        p.setProperty(Config.PREFIX + "passive.threshold", "0.85");
        p.setProperty(Config.PREFIX + "passive.threshold.supervisor", "0.95");
        p.setProperty(Config.PREFIX + "active.minChallenges.operator", "3");
        p.setProperty(Config.PREFIX + "maxRetries.resident", "5");
        LivenessEngine e = engineWith(p);
        assertEquals(0.85, e.policyFor(Workflow.RESIDENT).passiveThreshold(), 1e-9);
        assertEquals(0.95, e.policyFor(Workflow.SUPERVISOR).passiveThreshold(), 1e-9);
        assertEquals(3, e.policyFor(Workflow.OPERATOR).minChallenges());
        assertEquals(2, e.policyFor(Workflow.RESIDENT).minChallenges());
        assertEquals(5, e.policyFor(Workflow.RESIDENT).maxRetries());
    }
}
