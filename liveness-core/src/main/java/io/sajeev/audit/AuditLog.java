// #30 (WS1) | Bible §6.9, P-A | Append-only audit log with a SHA-256 hash chain and a verify step. Java 11 only.
// It refuses anything that could be an image, an embedding or an identifier. States and coarse bands only.
package io.sajeev.audit;

import io.sajeev.api.Workflow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** The liveness audit trail. Each record's hash covers the previous record's hash, so an edit or deletion shows up. */
public final class AuditLog {

    /** Attribute keys that must never appear, whatever the value. */
    private static final List<String> FORBIDDEN_KEYS = List.of("image", "frame", "jpeg", "embedding", "biometric", "template", "userid", "user", "name");
    /** A safe value: short, plain characters. Long base64-looking or array-looking strings are refused. */
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9_.:=\\- /]{0,64}");

    private final LongSupplier millis;
    private final List<String> lines = new ArrayList<>();
    private String lastHash = "0000000000000000000000000000000000000000000000000000000000000000";
    private long seq;

    /** @param millis wall-clock milliseconds for the record time (injected so tests are exact) */
    public AuditLog(LongSupplier millis) {
        this.millis = millis;
    }

    /**
     * Append one record.
     * @param event the event
     * @param workflow the workflow it belongs to
     * @param attrs extra attributes: states, coarse bands, counts. Never images, embeddings or identifiers.
     * @throws IllegalArgumentException if an attribute key or value looks unsafe
     */
    public synchronized void append(LivenessAuditEvent event, Workflow workflow, Map<String, String> attrs) {
        Map<String, String> sorted = new TreeMap<>(attrs);
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            String k = e.getKey().toLowerCase();
            for (String bad : FORBIDDEN_KEYS) {
                if (k.contains(bad)) {
                    throw new IllegalArgumentException("audit attribute not allowed: " + e.getKey());
                }
            }
            if (e.getValue() == null || !SAFE_VALUE.matcher(e.getValue()).matches()) {
                throw new IllegalArgumentException("audit value not allowed for " + e.getKey());
            }
        }
        seq++;
        StringBuilder body = new StringBuilder();
        body.append(seq).append('|').append(millis.getAsLong()).append('|').append(event.id()).append('|')
                .append(event.name()).append('|').append(workflow);
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            body.append('|').append(e.getKey()).append('=').append(e.getValue());
        }
        String hash = sha256(lastHash + body);
        lines.add(body + "|prev=" + lastHash + "|hash=" + hash);
        lastHash = hash;
    }

    /** @return the records, oldest first (read-only) */
    public synchronized List<String> lines() {
        return Collections.unmodifiableList(new ArrayList<>(lines));
    }

    /**
     * Check a log's hash chain, for example one read back from a file.
     * @param records the record lines
     * @return true if every record's hash matches and each links to the one before it
     */
    public static boolean verify(List<String> records) {
        String prev = "0000000000000000000000000000000000000000000000000000000000000000";
        for (String line : records) {
            int p = line.lastIndexOf("|prev=");
            int h = line.lastIndexOf("|hash=");
            if (p < 0 || h < p) {
                return false;
            }
            String body = line.substring(0, p);
            String claimedPrev = line.substring(p + 6, h);
            String claimedHash = line.substring(h + 6);
            if (!claimedPrev.equals(prev) || !sha256(prev + body).equals(claimedHash)) {
                return false;
            }
            prev = claimedHash;
        }
        return true;
    }

    private static String sha256(String s) {
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
