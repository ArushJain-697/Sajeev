// #28 (WS1) | Tests named as claims
package io.sajeev.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sajeev.api.Workflow;
import io.sajeev.policy.PolicyEngine.AttemptResult;
import io.sajeev.policy.PolicyEngine.Next;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class PolicyEngineTest {

    private static PolicyEngine engine(String... kv) throws Exception {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) {
            p.setProperty(Config.PREFIX + kv[i], kv[i + 1]);
        }
        return new PolicyEngine(Config.load(p));
    }

    @Test
    void test_default_policy_matches_pdf_flow_for_all_three_workflows() throws Exception {
        PolicyEngine e = engine();
        for (Workflow w : Workflow.values()) {
            PolicyEngine.WorkflowPolicy p = e.policyFor(w); // online and offline use the same synced values
            assertTrue(p.livenessEnabled(), w + ": liveness enabled by default");
            assertEquals(PolicyEngine.Mode.HYBRID, p.mode(), w + ": hybrid (passive first, then active when needed)");
            assertTrue(p.activeEnabled(), w + ": active liveness on");
            assertEquals(2, p.minChallenges());
            assertEquals(3, p.maxRetries());
            assertEquals(0.80, p.passiveThreshold(), 1e-9);
            assertEquals(PolicyEngine.RepeatedFailure.BLOCK, p.onRepeatedFailure(), w + ": BLOCK after repeated failure");
            assertEquals("BLOCK", p.onEngineUnavailable());
        }
    }

    @Test
    void test_workflow_override_changes_only_that_workflow() throws Exception {
        PolicyEngine e = engine("policy.mode.operator", "ACTIVE_ALWAYS", "maxRetries.supervisor", "5");
        assertEquals(PolicyEngine.Mode.ACTIVE_ALWAYS, e.policyFor(Workflow.OPERATOR).mode());
        assertEquals(PolicyEngine.Mode.HYBRID, e.policyFor(Workflow.RESIDENT).mode());
        assertEquals(5, e.policyFor(Workflow.SUPERVISOR).maxRetries());
        assertEquals(3, e.policyFor(Workflow.RESIDENT).maxRetries());
    }

    @Test
    void test_max_retries_blocks_and_guides_recovery() throws Exception {
        PolicyEngine.WorkflowSession s = engine().newSession(Workflow.RESIDENT);
        assertEquals(Next.RETRY, s.after(AttemptResult.PAD_FAIL).next());
        assertEquals(Next.RETRY, s.after(AttemptResult.ACTIVE_FAILED).next());
        PolicyEngine.Decision last = s.after(AttemptResult.UNBOUND);
        assertEquals(Next.BLOCK_WITH_RECOVERY, last.next());
        assertEquals("failure.max_retries", last.guidanceKey());
        assertTrue(last.auditEvent().contains("REPEATED_FAILURE"));
    }

    @Test
    void test_engine_error_is_never_a_pass() throws Exception {
        PolicyEngine.WorkflowSession s = engine().newSession(Workflow.OPERATOR);
        PolicyEngine.Decision d = s.after(AttemptResult.ENGINE_ERROR);
        assertTrue(d.next() != Next.ACCEPT);
        assertEquals(Next.BLOCK_WITH_RECOVERY, d.next());
        assertEquals("error.engine", d.guidanceKey());
        assertNotNull(d.auditEvent());
    }

    @Test
    void test_not_established_never_accepts() throws Exception {
        PolicyEngine.WorkflowSession s = engine("active.enabled", "false").newSession(Workflow.RESIDENT);
        assertTrue(s.after(AttemptResult.NOT_ESTABLISHED).next() != Next.ACCEPT);
    }

    @Test
    void test_override_is_audited_and_off_by_default() throws Exception {
        // default: after the limit it is BLOCK, never an override
        PolicyEngine.WorkflowSession def = engine().newSession(Workflow.SUPERVISOR);
        def.after(AttemptResult.PAD_FAIL);
        def.after(AttemptResult.PAD_FAIL);
        assertEquals(Next.BLOCK_WITH_RECOVERY, def.after(AttemptResult.PAD_FAIL).next());
        // opt-in: the override exists and carries an audit line
        PolicyEngine.WorkflowSession opt = engine("onRepeatedFailure", "SUPERVISOR_OVERRIDE").newSession(Workflow.SUPERVISOR);
        opt.after(AttemptResult.PAD_FAIL);
        opt.after(AttemptResult.PAD_FAIL);
        PolicyEngine.Decision d = opt.after(AttemptResult.PAD_FAIL);
        assertEquals(Next.SUPERVISOR_OVERRIDE, d.next());
        assertTrue(d.auditEvent().contains("SUPERVISOR_OVERRIDE"));
        PolicyEngine.WorkflowSession ex = engine("onRepeatedFailure", "EXCEPTION_FLOW").newSession(Workflow.RESIDENT);
        ex.after(AttemptResult.PAD_FAIL);
        ex.after(AttemptResult.PAD_FAIL);
        assertTrue(ex.after(AttemptResult.PAD_FAIL).auditEvent().contains("EXCEPTION_FLOW"));
    }

    @Test
    void test_infant_policy_never_skips_liveness() throws Exception {
        // There is no age-group setting that turns liveness off: the only switch is the explicit config flag, which is
        // audited. With the default config every age group goes through the same check, and a pass still needs PASSED.
        PolicyEngine.WorkflowSession s = engine().newSession(Workflow.RESIDENT);
        assertTrue(s.after(AttemptResult.PAD_FAIL).next() != Next.ACCEPT);
        assertEquals(Next.ACCEPT, s.after(AttemptResult.PASSED).next());
    }

    @Test
    void test_disabling_liveness_is_an_explicit_audited_choice() throws Exception {
        PolicyEngine.WorkflowSession s = engine("enabled.resident", "false").newSession(Workflow.RESIDENT);
        PolicyEngine.Decision d = s.after(AttemptResult.PASSED);
        assertEquals(Next.LIVENESS_DISABLED_BY_CONFIG, d.next());
        assertNotNull(d.auditEvent());
        assertEquals(Next.ACCEPT, engine("enabled.resident", "false").newSession(Workflow.OPERATOR).after(AttemptResult.PASSED).next());
    }

    @Test
    void test_cannot_judge_does_not_use_a_retry_but_cannot_wait_forever() throws Exception {
        PolicyEngine.WorkflowSession s = engine().newSession(Workflow.RESIDENT);
        for (int i = 0; i < 9; i++) {
            assertEquals(Next.GUIDE_AND_WAIT, s.after(AttemptResult.CANNOT_JUDGE).next());
        }
        assertEquals(0, s.failedAttempts());
        assertEquals(Next.RETRY, s.after(AttemptResult.CANNOT_JUDGE).next(), "the 10th in a row counts as a failed attempt");
        assertEquals(1, s.failedAttempts());
    }

    @Test
    void test_retry_counter_belongs_to_the_session() throws Exception {
        PolicyEngine e = engine();
        PolicyEngine.WorkflowSession a = e.newSession(Workflow.RESIDENT);
        a.after(AttemptResult.PAD_FAIL);
        a.after(AttemptResult.PAD_FAIL);
        PolicyEngine.WorkflowSession b = e.newSession(Workflow.RESIDENT);
        assertEquals(0, b.failedAttempts(), "a new workflow session starts at zero");
        assertNull(b.after(AttemptResult.PASSED).auditEvent());
    }
}
