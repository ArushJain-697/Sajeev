// #24 (WS1) | Tests named as claims
package io.sajeev.active;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sajeev.api.ChallengeType;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChallengeEngineTest {

    private static final List<ChallengeType> POOL =
            Arrays.asList(ChallengeType.BLINK, ChallengeType.SMILE, ChallengeType.TURN_HEAD_LEFT, ChallengeType.TURN_HEAD_RIGHT);

    private static ChallengePlanner planner() {
        return new ChallengePlanner(8.0, 3.0, new SecureRandom());
    }

    @Test
    void test_blink_never_issued_below_min_fps() {
        ChallengePlanner p = planner();
        for (int i = 0; i < 2000; i++) {
            assertFalse(p.draw(POOL, 5.0, 2).contains(ChallengeType.BLINK), "BLINK issued at 5 fps");
        }
        boolean seen = false;
        for (int i = 0; i < 2000 && !seen; i++) {
            seen = p.draw(POOL, 12.0, 2).contains(ChallengeType.BLINK);
        }
        assertTrue(seen, "BLINK should appear at 12 fps");
    }

    @Test
    void test_challenge_sequence_redrawn_each_attempt() {
        ChallengeEngine e = new ChallengeEngine(planner(), POOL, 3, 5000);
        Set<String> sequences = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            ChallengeSession s = e.newAttempt(15.0);
            assertNotNull(s);
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < 3; k++) {
                sb.append(s.current()).append(',');
                s.prompted(0, 0);
                s.onFrame(5, 10, true); // complete the challenge to reveal the next one
            }
            sequences.add(sb.toString());
        }
        assertTrue(sequences.size() > 1, "every attempt drew the same sequence");
    }

    @Test
    void test_action_before_prompt_does_not_count() {
        ChallengeSession s = new ChallengeSession(Arrays.asList(ChallengeType.SMILE), 5000);
        s.prompted(1_000_000_000L, 10);
        // frames 10 and 11 were already received or in flight when the prompt appeared: they cannot complete it
        assertEquals(ChallengeSession.Outcome.IN_PROGRESS, s.onFrame(10, 1_000_000_001L, true));
        assertEquals(ChallengeSession.Outcome.IN_PROGRESS, s.onFrame(11, 1_033_000_000L, true));
        // an action stamped before the prompt appeared also does not count
        assertEquals(ChallengeSession.Outcome.IN_PROGRESS, s.onFrame(12, 900_000_000L, true));
        assertEquals(ChallengeSession.Outcome.ACTIVE_PASS, s.onFrame(13, 1_100_000_000L, true));
    }

    @Test
    void test_ten_thousand_draws_cover_all_permutations_without_repeats() {
        List<ChallengeType> pool = Arrays.asList(ChallengeType.BLINK, ChallengeType.SMILE, ChallengeType.TURN_HEAD_LEFT, ChallengeType.TURN_HEAD_RIGHT);
        ChallengePlanner p = planner();
        Set<List<ChallengeType>> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            List<ChallengeType> d = p.draw(pool, 15.0, 3);
            assertEquals(3, d.size());
            assertEquals(3, new HashSet<>(d).size(), "a challenge repeated inside one draw: " + d);
            seen.add(d);
        }
        assertEquals(24, seen.size(), "4 types, 3 per draw: 4*3*2 = 24 ordered sequences must all appear");
    }

    @Test
    void test_timeout_reports_challenge_timeout() {
        ChallengeSession s = new ChallengeSession(Arrays.asList(ChallengeType.BLINK), 5000);
        s.prompted(0, 0);
        assertEquals(ChallengeSession.Outcome.IN_PROGRESS, s.onFrame(5, 4_900_000_000L, false));
        assertEquals(ChallengeSession.Outcome.CHALLENGE_TIMEOUT, s.onFrame(6, 5_100_000_000L, false));
    }

    @Test
    void test_too_small_a_pool_at_this_fps_gives_no_attempt_never_a_silent_pass() {
        ChallengeEngine e = new ChallengeEngine(planner(), Arrays.asList(ChallengeType.BLINK), 1, 5000);
        assertNull(e.newAttempt(5.0), "BLINK only, at 5 fps: there is nothing safe to ask");
    }

    @Test
    void test_planner_requires_a_secure_random() {
        assertThrows(IllegalArgumentException.class, () -> new ChallengePlanner(8, 3, null));
    }
}
