// #30 (WS1) | Bible §10 R3-4, P-J | Alert when one workstation keeps failing the PAD check. Java 11 only.
package io.sajeev.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Counts PAD failures per workstation in a sliding window. The workstation key is hashed; no person is identified. */
public final class AttemptOracleAlert {

    private final int threshold;
    private final long windowMillis;
    private final LongSupplier millis;
    private final Map<String, Deque<Long>> failures = new HashMap<>();

    /**
     * @param threshold PAD failures that raise the alert (TARGET 5)
     * @param windowMillis the sliding window (TARGET 10 minutes)
     * @param millis wall-clock milliseconds
     */
    public AttemptOracleAlert(int threshold, long windowMillis, LongSupplier millis) {
        this.threshold = threshold;
        this.windowMillis = windowMillis;
        this.millis = millis;
    }

    /**
     * Record one PAD failure.
     * @param workstationKey the machine ID or any workstation key (it is hashed here)
     * @return true when this failure brings the workstation to the alert threshold within the window
     */
    public synchronized boolean onPadFailure(String workstationKey) {
        long now = millis.getAsLong();
        Deque<Long> q = failures.computeIfAbsent(hash(workstationKey), k -> new ArrayDeque<>());
        q.addLast(now);
        while (!q.isEmpty() && now - q.peekFirst() > windowMillis) {
            q.removeFirst();
        }
        return q.size() >= threshold;
    }

    /** @return a short non-reversible tag for the audit line, so the alert can be matched without storing the machine ID */
    public static String tag(String workstationKey) {
        return hash(workstationKey).substring(0, 8);
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : d) {
                hex.append(String.format("%02x", b & 0xFF));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
