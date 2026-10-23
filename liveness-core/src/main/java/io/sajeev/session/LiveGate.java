// #54 (WS3) | Bible §8 | "Seen live, then stored" as a small object clients can use: one LIVE verdict allows one capture,
// and only if the captured face is bound to that session. Java 11 only (no Android or desktop types).
package io.sajeev.session;

/** Remembers the newest session that reached LIVE and lets exactly one capture use it. */
public final class LiveGate {

    /** Checks that the captured face is bound to the given live session. */
    public interface Binder {
        boolean bind(String sessionId);
    }

    private String live;

    /** @param sessionId a session that just reached LIVE */
    public synchronized void markLive(String sessionId) {
        live = sessionId;
    }

    /** Forget a live session (it ended some other way). */
    public synchronized void clear() {
        live = null;
    }

    /**
     * @param binder the binding check for the capture
     * @return true only if a live session exists AND the capture is bound to it; the live verdict is used up either way
     */
    public synchronized boolean allowStore(Binder binder) {
        String id = live;
        live = null;
        return id != null && binder.bind(id);
    }
}
