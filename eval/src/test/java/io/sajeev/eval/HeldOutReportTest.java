// #63 (WS4) | The report states its limits first and is produced by code.
package io.sajeev.eval;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class HeldOutReportTest {
    private static Presentation p(String f, String sp, boolean acc) {
        return new Presentation(f, sp, "s1", "dA", acc, acc ? "LIVE_PASSIVE" : "ATTACK_SUSPECTED", new HashMap<String, String>(),
                new HashMap<String, Double>(), false);
    }

    @Test
    void test_report_states_limits_first_and_lists_what_gets_through() {
        String md = HeldOutReport.markdown(Arrays.asList(p("a", "bona", true), p("b", "P1", false), p("c", "P1", true)), "h".repeat(64));
        assertTrue(md.indexOf("## Limits") < md.indexOf("## Error rates"), "limits must come first");
        assertTrue(md.contains("| P1 | 1 / 2 = 50.0%"), md);
        assertTrue(md.contains("| c | P1 | LIVE_PASSIVE"), md);
        assertTrue(md.contains("a rate of 0% only means no failure was seen"));
    }

    @Test
    void test_report_with_no_accepted_attack_says_so_without_overclaiming() {
        String md = HeldOutReport.markdown(Collections.singletonList(p("b", "P1", false)), "h".repeat(64));
        assertTrue(md.contains("No attack was accepted in this set (see the limits above)."));
    }
}
