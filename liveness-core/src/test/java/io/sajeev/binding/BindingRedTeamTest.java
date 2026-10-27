// Red-team 2 fixes in Binding: one person throughout the session, both signals on the SAME observation, timing edge cases.
package io.sajeev.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BindingRedTeamTest {

    private static final long SECOND = 1_000_000_000L;
    private static final double[] GEOM = {0.30, 0.55, 0.78};
    private static final float[] PERSON_A = {1, 0, 0, 0};
    private static final float[] PERSON_B = {0, 1, 0, 0};

    @Test
    void test_a_different_person_appearing_during_the_session_makes_the_capture_unbindable() {
        Binding b = new Binding(0.363, 0.08, 2000, 5);
        b.observeLive(PERSON_A, GEOM, 0.9); // the photo in the first frames
        b.observeLive(PERSON_B, GEOM, 0.9); // the accomplice swaps in
        b.observeLive(PERSON_B, GEOM, 0.9);
        b.liveVerdictAt(0L);
        assertTrue(b.identityChanged());
        // even a capture of the first person (or of the second) does not bind
        for (float[] capture : new float[][] {PERSON_A, PERSON_B}) {
            Binding.Result r = b.bind(1, capture, GEOM, SECOND, 5000);
            assertEquals(Binding.Verdict.CAPTURE_UNBOUND, r.verdict());
            assertTrue(r.failedChecks().contains("identity_changed"), r.failedChecks().toString());
        }
    }

    @Test
    void test_one_odd_frame_among_many_of_the_same_person_is_tolerated() {
        Binding b = new Binding(0.363, 0.08, 2000, 5);
        b.observeLive(PERSON_A, GEOM, 0.9);
        b.observeLive(PERSON_B, GEOM, 0.9); // a single glitch frame
        b.observeLive(PERSON_A, GEOM, 0.8);
        b.liveVerdictAt(0L);
        assertFalse(b.identityChanged());
        assertEquals(Binding.Verdict.BOUND, b.bind(1, PERSON_A, GEOM, SECOND, 5000).verdict());
    }

    @Test
    void test_a_capture_matching_embedding_on_one_entry_and_geometry_on_another_is_not_bound() {
        // threshold 0.9: entry 1 (A) passes the embedding only, entry 2 (A2, cosine 0.95 with A) passes the geometry only
        Binding b = new Binding(0.9, 0.08, 2000, 5);
        b.observeLive(new float[] {1, 0, 0, 0}, new double[] {0.40, 0.65, 0.70}, 0.9); // geometry far from the capture's
        b.observeLive(new float[] {0.95f, 0.3122f, 0, 0}, new double[] {0.30, 0.55, 0.78}, 0.8); // geometry like the capture's
        b.liveVerdictAt(0L);
        // the capture is identical to entry 1 in embedding (cos 1.0) and cos 0.95 to entry 2; geometry equals entry 2's.
        // Entry 2 satisfies both -> bound. So make the capture closer to entry 1 than the 0.9 bar allows for entry 2:
        float[] capture = {0.99f, -0.14107f, 0, 0}; // cos with entry 1 = 0.99, with entry 2 = 0.99*0.95 - 0.14107*0.3122 = 0.896
        Binding.Result r = b.bind(1, capture, new double[] {0.30, 0.55, 0.78}, SECOND, 5000);
        assertEquals(Binding.Verdict.CAPTURE_UNBOUND, r.verdict(), "matched two different observations: " + r.failedChecks());
        assertTrue(r.failedChecks().contains("geometry"), r.failedChecks().toString());
    }

    @Test
    void test_no_verdict_time_and_a_huge_timeout_cannot_overflow_into_a_pass() {
        Binding none = new Binding(0.363, 0.08, 2000, 5);
        none.observeLive(PERSON_A, GEOM, 0.9);
        assertTrue(none.bind(1, PERSON_A, GEOM, 0L, 5000).failedChecks().contains("timing"), "no verdict time must fail");
        Binding b = new Binding(0.363, 0.08, 2000, 5);
        b.observeLive(PERSON_A, GEOM, 0.9);
        b.liveVerdictAt(Long.MIN_VALUE + 10);
        assertTrue(b.bind(1, PERSON_A, GEOM, Long.MAX_VALUE, 5000).failedChecks().contains("timing"), "a difference that overflows must fail");
        b.clearVerdict();
        assertTrue(b.bind(1, PERSON_A, GEOM, 0L, 5000).failedChecks().contains("timing"), "a cleared verdict must fail");
        Binding neg = new Binding(0.363, 0.08, 2000, 5);
        neg.observeLive(PERSON_A, GEOM, 0.9);
        neg.liveVerdictAt(-5 * SECOND); // a legal negative monotonic time
        assertEquals(Binding.Verdict.BOUND, neg.bind(1, PERSON_A, GEOM, -4 * SECOND, 5000).verdict());
    }
}
