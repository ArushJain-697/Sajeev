// #54 (WS3) | Tests named as claims.
package io.sajeev.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LiveGateTest {

    @Test
    void test_android_capture_never_stored_without_binding() {
        LiveGate g = new LiveGate();
        AtomicInteger asked = new AtomicInteger();
        assertFalse(g.allowStore(id -> {
            asked.incrementAndGet();
            return true;
        }), "no live session: must refuse even if the binder would say yes");
        assertEquals(0, asked.get(), "binding must not even run without a live session");
        g.markLive("s1");
        assertFalse(g.allowStore(id -> false), "live but not bound: refuse");
        g.markLive("s2");
        assertTrue(g.allowStore(id -> id.equals("s2")), "live and bound: allow");
    }

    @Test
    void test_one_live_verdict_covers_one_capture_only() {
        LiveGate g = new LiveGate();
        g.markLive("s1");
        assertTrue(g.allowStore(id -> true));
        assertFalse(g.allowStore(id -> true), "the live verdict was already used");
    }

    @Test
    void test_a_failed_binding_also_uses_up_the_live_verdict() {
        LiveGate g = new LiveGate();
        g.markLive("s1");
        assertFalse(g.allowStore(id -> false));
        assertFalse(g.allowStore(id -> true), "a second try needs a new live check");
    }

    @Test
    void test_cleared_session_cannot_store() {
        LiveGate g = new LiveGate();
        g.markLive("s1");
        g.clear();
        assertFalse(g.allowStore(id -> true));
    }
}
