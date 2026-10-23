// #55/#56 (WS3) | Tests named as the ticket claims. They test the order of checks the Android Java layer uses.
package io.sajeev.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FaceAuthCheckTest {

    private final FaceAuthTicket ticket = new FaceAuthTicket(() -> 0L, 60_000);

    private FaceAuthCheck.Outcome run(String user, boolean liveAndBound, boolean matches, AtomicInteger matcherCalls) {
        return FaceAuthCheck.run(user, () -> liveAndBound, () -> {
            matcherCalls.incrementAndGet();
            return matches;
        }, ticket);
    }

    @Test
    void test_operator_face_login_blocked_when_liveness_fails_offline() {
        AtomicInteger calls = new AtomicInteger();
        // even a face that would match is refused when the liveness check failed; the match is never even run
        assertEquals(FaceAuthCheck.Outcome.LIVENESS_REFUSED, run("op1", false, true, calls));
        assertEquals(0, calls.get());
        assertFalse(ticket.consume("op1"), "no ticket, so the face login cannot complete");
    }

    @Test
    void test_supervisor_approval_blocked_when_liveness_fails() {
        AtomicInteger calls = new AtomicInteger();
        assertEquals(FaceAuthCheck.Outcome.LIVENESS_REFUSED, run("sup1", false, true, calls));
        assertFalse(ticket.consume("sup1"));
    }

    @Test
    void test_a_live_face_that_does_not_match_gets_no_ticket() {
        AtomicInteger calls = new AtomicInteger();
        assertEquals(FaceAuthCheck.Outcome.NO_MATCH, run("op1", true, false, calls));
        assertEquals(1, calls.get());
        assertFalse(ticket.consume("op1"));
    }

    @Test
    void test_only_a_live_bound_match_grants_the_ticket_for_that_user() {
        AtomicInteger calls = new AtomicInteger();
        assertEquals(FaceAuthCheck.Outcome.MATCH, run("op1", true, true, calls));
        assertFalse(ticket.consume("someone-else"), "the ticket is for op1 only");
        run("op1", true, true, calls);
        assertTrue(ticket.consume("op1"));
        assertFalse(ticket.consume("op1"), "single use");
    }
}
