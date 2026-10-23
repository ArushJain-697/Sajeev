// #55/#56 (WS3) | The order of a face authentication, as one pure function: live AND bound first, then the 1:1 match,
// and only a match grants the single-use ticket. Java 11 only.
package io.sajeev.session;

import java.util.function.BooleanSupplier;

/** Face authentication of one user. */
public final class FaceAuthCheck {

    /** What happened. Only MATCH grants the ticket. */
    public enum Outcome { MATCH, NO_MATCH, LIVENESS_REFUSED }

    private FaceAuthCheck() {
    }

    /**
     * @param userId the user being authenticated
     * @param liveAndBound true only if a live session exists and the captured face is bound to it (single-use)
     * @param matcher the 1:1 match against the user's onboarded face; not called unless liveAndBound is true
     * @param ticket receives the grant on MATCH
     * @return the outcome
     */
    public static Outcome run(String userId, BooleanSupplier liveAndBound, BooleanSupplier matcher, FaceAuthTicket ticket) {
        if (!liveAndBound.getAsBoolean()) {
            return Outcome.LIVENESS_REFUSED;
        }
        if (!matcher.getAsBoolean()) {
            return Outcome.NO_MATCH;
        }
        ticket.grant(userId);
        return Outcome.MATCH;
    }
}
