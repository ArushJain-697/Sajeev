// #14 (WS1) | Bible §7 | Tests named as claims
package io.sajeev.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class ConfigTest {

    @Test
    void test_default_config_matches_pdf_flow() throws Exception {
        Config c = Config.load(new Properties());
        assertEquals("HYBRID", c.value("policy.mode"));
        assertEquals("BLOCK", c.value("onRepeatedFailure"));
        assertEquals("true", c.value("enabled"));
        assertEquals("0.80", c.value("passive.threshold"));
        assertEquals("2", c.value("active.minChallenges"));
        assertEquals("3", c.value("maxRetries"));
        assertEquals("CONTINUOUS", c.value("frameProcessing"));
    }

    @Test
    void test_unknown_key_fails_fast() {
        Properties p = new Properties();
        p.setProperty(Config.PREFIX + "passive.threshhold", "0.9");
        assertThrows(Config.ConfigException.class, () -> Config.load(p));
    }

    @Test
    void test_workflow_override_wins_over_global() throws Exception {
        Properties p = new Properties();
        p.setProperty(Config.PREFIX + "passive.threshold", "0.80");
        p.setProperty(Config.PREFIX + "passive.threshold.operator", "0.90");
        Config c = Config.load(p);
        assertEquals("0.90", c.value("passive.threshold", "operator"));
        assertEquals("0.80", c.value("passive.threshold", "resident"));
        assertEquals("0.80", c.value("passive.threshold"));
    }

    @Test
    void test_invalid_threshold_is_refused() {
        Properties p = new Properties();
        p.setProperty(Config.PREFIX + "passive.threshold", "1.5");
        assertThrows(Config.ConfigException.class, () -> Config.load(p));
    }

    @Test
    void test_invalid_override_is_refused_even_when_global_is_valid() {
        Properties p = new Properties();
        p.setProperty(Config.PREFIX + "onRepeatedFailure.supervisor", "IGNORE");
        assertThrows(Config.ConfigException.class, () -> Config.load(p));
    }
}
