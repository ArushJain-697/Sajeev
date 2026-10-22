// #14 (WS1) | Bible §7 | Config keys, defaults, per-workflow overrides, validation. Java 11 only.
package io.sajeev.policy;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Every liveness configuration key with its default (Bible §7). Keys live under
 * {@code mosip.registration.liveness.}. A key can be overridden for one workflow by appending
 * {@code .resident}, {@code .operator} or {@code .supervisor}. The override wins over the global value.
 */
public final class Config {

    /** Common prefix for every liveness key. */
    public static final String PREFIX = "mosip.registration.liveness.";

    /** Workflow suffixes allowed for per-workflow overrides. */
    public static final List<String> WORKFLOWS = Collections.unmodifiableList(
            Arrays.asList("resident", "operator", "supervisor"));

    /** Defaults. The PDF flow is hybrid for every workflow, and the repeated-failure behaviour is BLOCK. */
    public static final Map<String, String> DEFAULTS;

    static {
        Map<String, String> d = new TreeMap<>();
        d.put("enabled", "true");
        d.put("passive.threshold", "0.80");
        d.put("passive.minLiveVotes", "3");
        d.put("passive.voterWeights", "equal");
        d.put("active.enabled", "true");
        d.put("active.minChallenges", "2");
        d.put("active.challengeTypes", "BLINK,SMILE,TURN_HEAD_LEFT,TURN_HEAD_RIGHT,LOOK_LEFT,LOOK_RIGHT,LOOK_UP,LOOK_DOWN,EYES_CLOSED_HOLD,OPEN_MOUTH");
        d.put("active.challengeTimeoutMs", "5000");
        d.put("maxRetries", "3");
        d.put("policy.mode", "HYBRID");
        d.put("onRepeatedFailure", "BLOCK");
        d.put("frameProcessing", "CONTINUOUS");
        d.put("minFps", "3.0");
        d.put("binding.embedding.threshold", "0.363");
        d.put("binding.geometry.tolerance", "calibrated-on-DEV");
        d.put("binding.windowMarginMs", "2000");
        d.put("onEngineUnavailable", "BLOCK");
        d.put("guidance.overrides", "none");
        d.put("diagnostic.enabled", "false");
        d.put("metrics.enabled", "true");
        d.put("acceleration", "AUTO");
        d.put("model.manifest", "models/manifest.json");
        DEFAULTS = Collections.unmodifiableMap(d);
    }

    /** Global effective values, after defaults and global properties. */
    private final Map<String, String> global;
    /** Per-workflow effective values: only the keys that are overridden. */
    private final Map<String, Map<String, String>> overrides;

    private Config(Map<String, String> global, Map<String, Map<String, String>> overrides) {
        this.global = Collections.unmodifiableMap(global);
        this.overrides = overrides;
    }

    /**
     * Build the configuration from properties. Unknown keys fail fast; they are never ignored.
     * @param props the loaded properties (may be empty)
     * @return validated config
     * @throws ConfigException on an unknown key or an invalid value
     */
    public static Config load(Properties props) throws ConfigException {
        for (String name : props.stringPropertyNames()) {
            if (name.startsWith(PREFIX) && !isKnown(name.substring(PREFIX.length()))) {
                throw new ConfigException("unknown liveness key: " + name);
            }
        }
        Map<String, String> global = new HashMap<>(DEFAULTS);
        for (String key : DEFAULTS.keySet()) {
            String v = props.getProperty(PREFIX + key);
            if (v != null) {
                global.put(key, v.trim());
            }
        }
        Map<String, Map<String, String>> overrides = new HashMap<>();
        for (String w : WORKFLOWS) {
            Map<String, String> o = new TreeMap<>();
            for (String key : DEFAULTS.keySet()) {
                String v = props.getProperty(PREFIX + key + "." + w);
                if (v != null) {
                    o.put(key, v.trim());
                }
            }
            overrides.put(w, o);
        }
        validate(global);
        for (String w : WORKFLOWS) {
            Map<String, String> effective = new HashMap<>(global);
            effective.putAll(overrides.get(w));
            validate(effective);
        }
        return new Config(global, overrides);
    }

    /**
     * Effective value of a key for a workflow: the workflow override if set, else the global value.
     * @param key a key from DEFAULTS, for example "passive.threshold"
     * @param workflow "resident", "operator" or "supervisor", or null for the global value
     * @return the effective value
     */
    public String value(String key, String workflow) {
        if (!DEFAULTS.containsKey(key)) {
            throw new IllegalArgumentException("not a liveness key: " + key);
        }
        if (workflow != null && overrides.containsKey(workflow) && overrides.get(workflow).containsKey(key)) {
            return overrides.get(workflow).get(key);
        }
        return global.get(key);
    }

    /** @param key a liveness key @return the global effective value */
    public String value(String key) {
        return value(key, null);
    }

    /** @return the global effective values, sorted by key (used to generate docs/CONFIG.md) */
    public Map<String, String> all() {
        return global;
    }

    private static boolean isKnown(String rest) {
        for (String key : DEFAULTS.keySet()) {
            if (rest.equals(key)) {
                return true;
            }
            for (String w : WORKFLOWS) {
                if (rest.equals(key + "." + w)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void validate(Map<String, String> v) throws ConfigException {
        double t = Double.parseDouble(v.get("passive.threshold"));
        if (t < 0.0 || t > 1.0) {
            throw new ConfigException("passive.threshold must be in [0, 1]");
        }
        if (Integer.parseInt(v.get("active.minChallenges")) < 0) {
            throw new ConfigException("active.minChallenges must be >= 0");
        }
        if (Integer.parseInt(v.get("maxRetries")) < 0) {
            throw new ConfigException("maxRetries must be >= 0");
        }
        if (Long.parseLong(v.get("active.challengeTimeoutMs")) <= 0L) {
            throw new ConfigException("active.challengeTimeoutMs must be > 0");
        }
        if (Double.parseDouble(v.get("minFps")) <= 0.0) {
            throw new ConfigException("minFps must be > 0");
        }
        String onFail = v.get("onRepeatedFailure");
        if (!"BLOCK".equals(onFail) && !"SUPERVISOR_OVERRIDE".equals(onFail) && !"EXCEPTION_FLOW".equals(onFail)) {
            throw new ConfigException("onRepeatedFailure must be BLOCK, SUPERVISOR_OVERRIDE or EXCEPTION_FLOW");
        }
    }

    /** Thrown when configuration has an unknown key or an invalid value. */
    public static final class ConfigException extends Exception {
        private static final long serialVersionUID = 1L;

        /** @param message what is wrong */
        public ConfigException(String message) {
            super(message);
        }
    }
}
