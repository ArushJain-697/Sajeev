// #55/#56 (WS3) | A face check that matched grants ONE short-lived ticket for that user; the password-free login or approval
// must spend it. The UI cannot claim "face matched": only the engine side can grant a ticket. Java 11 only.
package io.sajeev.session;

import java.util.function.LongSupplier;

/** Single-use, time-limited permission for one user to finish a face-based login or approval. */
public final class FaceAuthTicket {

    private final LongSupplier clockMillis;
    private final long ttlMillis;
    private String user;
    private long grantedAt;

    /**
     * @param clockMillis a clock (System::currentTimeMillis in production)
     * @param ttlMillis how long a ticket stays valid (60 000 TARGET)
     */
    public FaceAuthTicket(LongSupplier clockMillis, long ttlMillis) {
        this.clockMillis = clockMillis;
        this.ttlMillis = ttlMillis;
    }

    /** @param userId the user whose face matched live and bound; replaces any earlier ticket */
    public synchronized void grant(String userId) {
        user = userId;
        grantedAt = clockMillis.getAsLong();
    }

    /** Forget any ticket. */
    public synchronized void clear() {
        user = null;
    }

    /**
     * @param userId the user trying to finish the login or approval
     * @return true once, if a ticket for exactly this user exists and has not expired; the ticket is used up either way
     */
    public synchronized boolean consume(String userId) {
        String u = user;
        long t = grantedAt;
        user = null;
        return u != null && u.equals(userId) && clockMillis.getAsLong() - t <= ttlMillis;
    }
}
