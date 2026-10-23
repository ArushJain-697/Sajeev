// #55/#56 (WS3) | Tests named as claims.
package io.sajeev.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FaceAuthTicketTest {

    @Test
    void test_no_ticket_means_no_password_free_login_or_approval() {
        FaceAuthTicket t = new FaceAuthTicket(() -> 0L, 60_000);
        assertFalse(t.consume("alice"));
    }

    @Test
    void test_a_ticket_is_for_one_user_and_one_use() {
        FaceAuthTicket t = new FaceAuthTicket(() -> 0L, 60_000);
        t.grant("alice");
        assertFalse(t.consume("bob"), "another user must not spend alice's ticket");
        assertFalse(t.consume("alice"), "the failed try used the ticket up");
        t.grant("alice");
        assertTrue(t.consume("alice"));
        assertFalse(t.consume("alice"), "second use");
    }

    @Test
    void test_an_expired_ticket_is_refused() {
        long[] now = {0};
        FaceAuthTicket t = new FaceAuthTicket(() -> now[0], 60_000);
        t.grant("alice");
        now[0] = 60_001;
        assertFalse(t.consume("alice"));
    }

    @Test
    void test_clear_drops_the_ticket() {
        FaceAuthTicket t = new FaceAuthTicket(() -> 0L, 60_000);
        t.grant("alice");
        t.clear();
        assertFalse(t.consume("alice"));
    }
}
