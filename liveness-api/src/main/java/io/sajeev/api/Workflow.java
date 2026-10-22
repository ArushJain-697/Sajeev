// #13 (WS1) | Bible §6, §9 | Java 11 only. Frozen API: changes need a BUILD_LOG.md note.
package io.sajeev.api;

/** The three face workflows that use liveness (PDF Mandatory 1, O2). */
public enum Workflow {
    /** Resident registration capture. */
    RESIDENT,
    /** Operator login and post-registration authentication. */
    OPERATOR,
    /** Supervisor approval and end-of-day authentication. */
    SUPERVISOR
}
