// #30 (WS1) | Tests named as claims
package io.sajeev.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sajeev.api.Workflow;
import io.sajeev.metrics.LivenessMetrics;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuditAndMetricsTest {

    @Test
    void test_audit_ids_never_collide_with_existing_ids() throws Exception {
        // 154 audit IDs read from MOSIP registration-client 1.2.0.x (testdata/reference); prefixes REG-AUTH, EVT, NAV, NOT, PKT, SCH, SYNC, UPG
        Set<String> existing = new HashSet<>(Files.readAllLines(Paths.get("../testdata/reference/mosip_audit_ids_1.2.0.x.txt")));
        assertEquals(154, existing.size());
        Set<String> ours = new HashSet<>();
        for (LivenessAuditEvent e : LivenessAuditEvent.values()) {
            assertTrue(e.id().startsWith("REG-LIV-"), e.id());
            assertFalse(existing.contains(e.id()), "collides with " + e.id());
            assertTrue(ours.add(e.id()), "duplicate ID " + e.id());
        }
        for (String id : existing) {
            assertFalse(id.startsWith("REG-LIV-"), "MOSIP already uses our prefix: " + id);
        }
    }

    @Test
    void test_audit_never_contains_image_or_embedding() {
        AuditLog log = new AuditLog(() -> 1_000L);
        Map<String, String> ok = new HashMap<>();
        ok.put("passive_band", ScoreBand.of(0.91).name());
        ok.put("challenges", "2");
        log.append(LivenessAuditEvent.REG_LIVENESS_PASSIVE_RESULT, Workflow.RESIDENT, ok);
        Map<String, String> emb = new HashMap<>();
        emb.put("embedding", "0.12,0.34");
        assertThrows(IllegalArgumentException.class, () -> log.append(LivenessAuditEvent.REG_LIVENESS_BINDING, Workflow.RESIDENT, emb));
        Map<String, String> img = new HashMap<>();
        img.put("note", "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAAMCAgMCAgMDAwMEAwMEBQgFBQQEBQoHBwYIDAoMDAsKCwsNDhIQDQ4RDgsLEBYQERMUFRUVDA8XGBYUGBIUFRT/");
        assertThrows(IllegalArgumentException.class, () -> log.append(LivenessAuditEvent.REG_LIVENESS_BINDING, Workflow.RESIDENT, img));
        Map<String, String> user = new HashMap<>();
        user.put("userId", "op01");
        assertThrows(IllegalArgumentException.class, () -> log.append(LivenessAuditEvent.REG_LIVENESS_START, Workflow.OPERATOR, user));
        assertEquals(1, log.lines().size(), "refused records must not be written");
    }

    @Test
    void test_scores_are_logged_as_coarse_bands_only() {
        assertEquals(ScoreBand.LOW, ScoreBand.of(0.12));
        assertEquals(ScoreBand.MID, ScoreBand.of(0.79));
        assertEquals(ScoreBand.HIGH, ScoreBand.of(0.80));
    }

    @Test
    void test_hash_chain_detects_edits_and_deletions() {
        AuditLog log = new AuditLog(() -> 5L);
        for (int i = 0; i < 4; i++) {
            Map<String, String> a = new HashMap<>();
            a.put("attempt", String.valueOf(i));
            log.append(LivenessAuditEvent.REG_LIVENESS_RETRY, Workflow.RESIDENT, a);
        }
        List<String> lines = log.lines();
        assertTrue(AuditLog.verify(lines));
        List<String> edited = new ArrayList<>(lines);
        edited.set(1, edited.get(1).replace("attempt=1", "attempt=9"));
        assertFalse(AuditLog.verify(edited), "an edited record must break the chain");
        List<String> deleted = new ArrayList<>(lines);
        deleted.remove(1);
        assertFalse(AuditLog.verify(deleted), "a deleted record must break the chain");
        assertTrue(AuditLog.verify(Collections.<String>emptyList()));
    }

    @Test
    void test_diagnostics_off_by_default() {
        DiagnosticMode off = new DiagnosticMode(false);
        assertFalse(off.visibleTo(DiagnosticMode.Audience.OPERATOR));
        assertEquals("", off.banner());
        Map<String, String> data = new HashMap<>();
        data.put("fps", "15");
        assertTrue(off.panelFor(DiagnosticMode.Audience.OPERATOR, data).isEmpty());
    }

    @Test
    void test_diagnostics_when_on_are_for_the_operator_only_and_never_in_packets() {
        DiagnosticMode on = new DiagnosticMode(true);
        Map<String, String> data = new HashMap<>();
        data.put("fps", "15");
        assertEquals(DiagnosticMode.BANNER, on.banner());
        assertFalse(on.panelFor(DiagnosticMode.Audience.OPERATOR, data).isEmpty());
        assertTrue(on.panelFor(DiagnosticMode.Audience.RESIDENT, data).isEmpty(), "the resident never sees diagnostics");
        assertTrue(on.forPacket(data).isEmpty(), "diagnostics are never stored in packets");
    }

    @Test
    void test_metrics_contain_no_user_identifier() {
        LivenessMetrics m = new LivenessMetrics();
        m.recordAttempt(Workflow.RESIDENT, 1800, true, false);
        m.recordAttempt(Workflow.OPERATOR, 2400, false, true);
        m.recordChallenge(1500);
        String out = m.export();
        Set<String> keys = new HashSet<>();
        for (String line : out.split("\n")) {
            keys.add(line.substring(0, line.indexOf('=')));
        }
        assertEquals(new HashSet<>(List.of("attempts", "avg_processing_ms", "avg_challenge_ms", "retry_rate", "failure_rate",
                "count_resident", "count_operator", "count_supervisor")), keys);
        // no public method takes a string: nothing identifying a person, operator, user or machine can be passed in
        for (Method method : LivenessMetrics.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                for (Class<?> p : method.getParameterTypes()) {
                    assertFalse(p == String.class, method.getName() + " accepts a String");
                }
            }
        }
        assertTrue(out.contains("avg_processing_ms=2100"));
        assertTrue(out.contains("failure_rate=0.500"));
        assertTrue(out.contains("retry_rate=0.500"));
    }

    @Test
    void test_repeated_pad_failures_from_one_workstation_raise_an_alert() {
        long[] now = {0L};
        AttemptOracleAlert alert = new AttemptOracleAlert(5, 600_000L, () -> now[0]);
        for (int i = 0; i < 4; i++) {
            assertFalse(alert.onPadFailure("WS-1"));
            now[0] += 30_000L;
        }
        assertTrue(alert.onPadFailure("WS-1"), "the fifth failure inside ten minutes raises the alert");
        assertFalse(alert.onPadFailure("WS-2"), "a different workstation has its own count");
        now[0] += 3_600_000L;
        assertFalse(alert.onPadFailure("WS-1"), "failures older than the window are forgotten");
        assertEquals(8, AttemptOracleAlert.tag("WS-1").length());
        assertFalse(AttemptOracleAlert.tag("WS-1").contains("WS-1"));
    }
}
