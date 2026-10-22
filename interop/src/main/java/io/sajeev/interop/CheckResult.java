// #39 (WS4) | One contract check outcome. Java 11 only.
package io.sajeev.interop;

/** PASS, FAIL or SKIP for one named check, with a short detail line. */
public final class CheckResult {
    /** Outcome of a check. SKIP means the harness could not run it (not a pass). */
    public enum Status { PASS, FAIL, SKIP }

    private final String name;
    private final Status status;
    private final String detail;

    public CheckResult(String name, Status status, String detail) {
        this.name = name;
        this.status = status;
        this.detail = detail;
    }

    public String name() { return name; }
    public Status status() { return status; }
    public String detail() { return detail; }
}
