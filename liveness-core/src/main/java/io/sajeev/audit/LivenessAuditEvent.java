// #30 (WS1) | Bible §6.9 | Liveness audit events with their own ID block REG-LIV-nnn. Java 11 only.
// MOSIP's existing IDs use the prefixes REG-AUTH, REG-EVT, REG-NAV, REG-NOT, REG-PKT, REG-SCH, REG-SYNC, REG-UPG (1.2.0.x).
// REG-LIV is a new prefix, so these IDs cannot collide with any of them.
package io.sajeev.audit;

/** The events, in the order a session produces them. */
public enum LivenessAuditEvent {
    REG_LIVENESS_START("REG-LIV-001"),
    REG_LIVENESS_PASSIVE_RESULT("REG-LIV-002"),
    REG_LIVENESS_CHALLENGE_PROMPTED("REG-LIV-003"),
    REG_LIVENESS_CHALLENGE_RESULT("REG-LIV-004"),
    REG_LIVENESS_BINDING("REG-LIV-005"),
    REG_LIVENESS_VERDICT("REG-LIV-006"),
    REG_LIVENESS_RETRY("REG-LIV-007"),
    REG_LIVENESS_OVERRIDE("REG-LIV-008"),
    REG_LIVENESS_ENGINE_UNAVAILABLE("REG-LIV-009"),
    REG_LIVENESS_ALERT_REPEATED_FAILURE("REG-LIV-010"),
    REG_LIVENESS_DIAGNOSTIC_ENABLED("REG-LIV-011");

    private final String id;

    LivenessAuditEvent(String id) {
        this.id = id;
    }

    /** @return the audit ID, for example REG-LIV-006 */
    public String id() {
        return id;
    }
}
