// #30 (WS1) | Bible §6.9 | Anonymised operational metrics. No user identifier can be passed in. Java 11 only.
package io.sajeev.metrics;

import io.sajeev.api.Workflow;
import java.util.EnumMap;
import java.util.Map;

/** Aggregates only. There is no method that accepts a person, an operator, a user ID or a machine ID. */
public final class LivenessMetrics {

    private long attempts;
    private long failures;
    private long retries;
    private long processingMillisTotal;
    private long processingSamples;
    private long challengeMillisTotal;
    private long challengeSamples;
    private final Map<Workflow, Long> perWorkflow = new EnumMap<>(Workflow.class);

    /**
     * Record one finished attempt.
     * @param workflow which workflow
     * @param processingMillis time from the first frame to the verdict
     * @param passed whether the attempt ended with a pass
     * @param wasRetry whether this attempt was a retry
     */
    public synchronized void recordAttempt(Workflow workflow, long processingMillis, boolean passed, boolean wasRetry) {
        attempts++;
        if (!passed) {
            failures++;
        }
        if (wasRetry) {
            retries++;
        }
        processingMillisTotal += processingMillis;
        processingSamples++;
        perWorkflow.merge(workflow, 1L, Long::sum);
    }

    /** @param millis time to complete one challenge */
    public synchronized void recordChallenge(long millis) {
        challengeMillisTotal += millis;
        challengeSamples++;
    }

    /** @return the aggregates as plain text lines, key=value, safe to export. Nothing here identifies a person. */
    public synchronized String export() {
        StringBuilder sb = new StringBuilder();
        sb.append("attempts=").append(attempts).append('\n');
        sb.append("avg_processing_ms=").append(processingSamples == 0 ? 0 : processingMillisTotal / processingSamples).append('\n');
        sb.append("avg_challenge_ms=").append(challengeSamples == 0 ? 0 : challengeMillisTotal / challengeSamples).append('\n');
        sb.append("retry_rate=").append(rate(retries, attempts)).append('\n');
        sb.append("failure_rate=").append(rate(failures, attempts)).append('\n');
        for (Workflow w : Workflow.values()) {
            sb.append("count_").append(w.name().toLowerCase()).append('=').append(perWorkflow.getOrDefault(w, 0L)).append('\n');
        }
        return sb.toString();
    }

    private static String rate(long part, long whole) {
        return whole == 0 ? "0.000" : String.format("%.3f", (double) part / whole);
    }
}
