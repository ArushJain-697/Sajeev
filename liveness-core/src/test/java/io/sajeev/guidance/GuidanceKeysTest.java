// #29 (WS1) | Tests named as claims
package io.sajeev.guidance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sajeev.api.ErrorState;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class GuidanceKeysTest {

    @Test
    void test_every_key_is_present_in_all_three_languages() throws Exception {
        GuidanceKeys g = new GuidanceKeys();
        assertEquals(new TreeSet<>(g.keys("en")), new TreeSet<>(g.keys("fr")));
        assertEquals(new TreeSet<>(g.keys("en")), new TreeSet<>(g.keys("ar")));
    }

    @Test
    void test_every_error_state_has_a_message_key() throws Exception {
        GuidanceKeys g = new GuidanceKeys();
        for (ErrorState s : ErrorState.values()) {
            String key = GuidanceKeys.keyFor(s);
            assertNotNull(key, "no key for " + s);
            assertNotNull(g.text(key, "en"), "no English text for " + key);
        }
    }

    @Test
    void test_pdf_example_texts_are_exact() throws Exception {
        GuidanceKeys g = new GuidanceKeys();
        assertEquals("Please blink", g.text("challenge.blink", "en"));
        assertEquals("Please turn your head to the left", g.text("challenge.turn_left", "en"));
        assertEquals("Checking face liveness…", g.text("passive.checking", "en"));
        assertEquals("Please continue", g.text("feedback.continue", "en"));
        assertEquals("Action detected", g.text("feedback.action_detected", "en"));
        assertEquals("Hold still", g.text("feedback.hold_still", "en"));
        assertEquals("Good, face captured successfully", g.text("feedback.captured", "en"));
        assertEquals("We could not verify face liveness. Please try again.", g.text("failure.liveness", "en"));
        assertEquals("Face verification could not be completed. Please try again.", g.text("failure.pad", "en"));
    }

    @Test
    void test_arabic_is_right_to_left_and_english_is_not() {
        assertTrue(GuidanceKeys.isRtl("ar"));
        assertFalse(GuidanceKeys.isRtl("en"));
        assertFalse(GuidanceKeys.isRtl("fr"));
    }

    @Test
    void test_every_challenge_has_a_spoken_twin() throws Exception {
        GuidanceKeys g = new GuidanceKeys();
        for (String k : g.keys("en")) {
            if (k.startsWith("challenge.")) {
                String spoken = "spoken." + k;
                assertTrue(g.keys("en").contains(spoken), "no spoken prompt for " + k);
                assertEquals(g.text(k, "en"), g.text(spoken, "en"));
            }
        }
    }
}
