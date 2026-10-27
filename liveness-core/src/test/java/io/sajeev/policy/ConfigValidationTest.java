// Red-team 2 fix: every numeric and enum key is validated when the configuration is loaded, never at session time.
package io.sajeev.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class ConfigValidationTest {

    private static void bad(String key, String value) {
        Properties p = new Properties();
        p.setProperty(Config.PREFIX + key, value);
        assertThrows(Config.ConfigException.class, () -> Config.load(p), key + "=" + value + " was accepted");
    }

    private static void good(String key, String value) {
        Properties p = new Properties();
        p.setProperty(Config.PREFIX + key, value);
        assertDoesNotThrow(() -> Config.load(p), key + "=" + value + " was refused");
    }

    @Test
    void test_the_binding_embedding_threshold_must_be_between_0_and_1() {
        bad("binding.embedding.threshold", "-1");
        bad("binding.embedding.threshold", "1.5");
        bad("binding.embedding.threshold", "abc");
        bad("binding.embedding.threshold", "NaN");
        good("binding.embedding.threshold", "0.4");
    }

    @Test
    void test_minLiveVotes_must_be_between_1_and_5() {
        bad("passive.minLiveVotes", "0");
        bad("passive.minLiveVotes", "6");
        bad("passive.minLiveVotes", "three");
        good("passive.minLiveVotes", "1");
        good("passive.minLiveVotes", "5");
    }

    @Test
    void test_voter_weights_must_be_positive_numbers_and_parse_errors_are_config_errors() {
        bad("passive.voterWeights", "1,0,1,1,1");
        bad("passive.voterWeights", "1,-2,1");
        bad("passive.voterWeights", "a,b");
        bad("passive.voterWeights", "1,,1");
        bad("passive.voterWeights", "1,1,1,1,1,1");
        good("passive.voterWeights", "1,2,1,1,1");
        good("passive.voterWeights", "equal");
    }

    @Test
    void test_challenge_timeout_has_bounds_and_must_be_a_number() {
        bad("active.challengeTimeoutMs", "0");
        bad("active.challengeTimeoutMs", "100");
        bad("active.challengeTimeoutMs", "99999999");
        bad("active.challengeTimeoutMs", "soon");
        good("active.challengeTimeoutMs", "8000");
    }

    @Test
    void test_frame_processing_and_acceleration_are_enums() {
        bad("frameProcessing", "SOMETIMES");
        bad("acceleration", "TURBO");
        good("frameProcessing", "PERIODIC");
        good("acceleration", "CPU");
        good("acceleration", "COREML");
    }

    @Test
    void test_other_numbers_that_do_not_parse_are_config_errors_not_runtime_exceptions() {
        bad("passive.threshold", "high");
        bad("maxRetries", "many");
        bad("minFps", "fast");
        bad("binding.geometry.tolerance", "wide");
        bad("binding.windowMarginMs", "-5");
        bad("active.challengeTypes", "BLINK,FLY");
        good("binding.geometry.tolerance", "0.1");
    }
}
