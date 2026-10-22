// #13 (WS1) | Tests named as claims
package io.sajeev.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FrameAndVoteTest {
    @Test
    void test_frame_copies_its_bytes_so_callers_cannot_change_it() {
        byte[] original = {1, 2, 3};
        Frame frame = new Frame(original, 100L, 1L);
        original[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, frame.jpeg());
    }

    @Test
    void test_frame_rejects_empty_jpeg() {
        assertThrows(IllegalArgumentException.class, () -> new Frame(new byte[0], 1L, 1L));
    }

    @Test
    void test_vote_rejects_probability_outside_zero_to_one() {
        assertThrows(IllegalArgumentException.class, () -> new Vote(Vote.Label.LIVE, 1.5, "bad"));
    }

    @Test
    void test_abstain_is_allowed_without_a_probability() {
        new Vote(Vote.Label.ABSTAIN, 0.0, "cannot judge");
    }

    @Test
    void test_error_state_has_all_thirteen_pdf_cases() {
        assertThrows(IllegalArgumentException.class, () -> ErrorState.valueOf("NOT_A_STATE"));
        org.junit.jupiter.api.Assertions.assertEquals(13, ErrorState.values().length);
    }

    @Test
    void test_challenge_pool_has_ten_types() {
        org.junit.jupiter.api.Assertions.assertEquals(10, ChallengeType.values().length);
    }
}
