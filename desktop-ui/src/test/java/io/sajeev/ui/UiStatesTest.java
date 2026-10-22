// #41 (WS2) | Tests named as claims. Pure model tests: no display needed.
package io.sajeev.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sajeev.api.ErrorState;
import io.sajeev.guidance.GuidanceKeys;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiStatesTest {

    private static void checkText(GuidanceKeys g, String lang, UiState s) {
        for (String k : new String[] {s.titleKey(), s.badgeKey(), s.messageKey()}) {
            String t = g.text(k, lang);
            assertTrue(t != null && !t.trim().isEmpty(), "blank text for " + k + " in " + lang);
        }
        for (String k : s.hintKeys()) {
            assertFalse(g.text(k, lang).isEmpty(), "blank hint " + k);
        }
        for (String k : s.buttonKeys()) {
            assertFalse(g.text(k, lang).isEmpty(), "blank button " + k);
        }
        if (!s.footerKey().isEmpty()) {
            assertFalse(g.text(s.footerKey(), lang).isEmpty());
        }
    }

    @Test
    void test_every_error_state_renders_a_message() throws Exception {
        GuidanceKeys g = new GuidanceKeys();
        for (String lang : GuidanceKeys.LANGS) {
            for (ErrorState e : ErrorState.values()) {
                checkText(g, lang, UiStates.forError(e));
            }
        }
    }

    @Test
    void test_all_fourteen_ux_screens_have_text_in_every_language() throws Exception {
        GuidanceKeys g = new GuidanceKeys();
        List<UiState> all = UiStates.allUx();
        assertEquals(14, all.size());
        for (String lang : GuidanceKeys.LANGS) {
            for (UiState s : all) {
                checkText(g, lang, s);
            }
        }
    }

    @Test
    void test_pad_failure_screen_shows_no_detail() throws Exception {
        UiState s = UiStates.ux10();
        GuidanceKeys g = new GuidanceKeys();
        assertEquals("Face verification could not be completed. Please try again.", g.text(s.messageKey(), "en"));
        assertTrue(s.hintKeys().isEmpty(), "PAD failure must not list hints that reveal what was detected");
    }

    @Test
    void test_no_screen_relies_on_colour_alone() {
        for (UiState s : UiStates.allUx()) {
            assertFalse(s.badgeKey().isEmpty(), "every screen has a text badge");
        }
    }
}
