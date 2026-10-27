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

    /** Least and largest challenge timeout (ms) that is accepted. */
    public static final long MIN_CHALLENGE_TIMEOUT_MS = 500L;
    public static final long MAX_CHALLENGE_TIMEOUT_MS = 120_000L;

    private static double num(Map<String, String> v, String key) throws ConfigException {
        try {
            double d = Double.parseDouble(v.get(key));
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new ConfigException(key + " must be a finite number");
            }
            return d;
        } catch (NumberFormatException e) {
            throw new ConfigException(key + " must be a number, got '" + v.get(key) + "'");
        }
    }

    private static long whole(Map<String, String> v, String key) throws ConfigException {
        try {
            return Long.parseLong(v.get(key));
        } catch (NumberFormatException e) {
            throw new ConfigException(key + " must be a whole number, got '" + v.get(key) + "'");
        }
    }

    private static void validate(Map<String, String> v) throws ConfigException {
        double t = num(v, "passive.threshold");
        if (t < 0.0 || t > 1.0) {
            throw new ConfigException("passive.threshold must be in [0, 1]");
        }
        if (whole(v, "active.minChallenges") < 0) {
            throw new ConfigException("active.minChallenges must be >= 0");
        }
        if (whole(v, "maxRetries") < 0) {
            throw new ConfigException("maxRetries must be >= 0");
        }
        long timeout = whole(v, "active.challengeTimeoutMs");
        if (timeout < MIN_CHALLENGE_TIMEOUT_MS || timeout > MAX_CHALLENGE_TIMEOUT_MS) {
            throw new ConfigException("active.challengeTimeoutMs must be between " + MIN_CHALLENGE_TIMEOUT_MS + " and "
                    + MAX_CHALLENGE_TIMEOUT_MS);
        }
        if (num(v, "minFps") <= 0.0) {
            throw new ConfigException("minFps must be > 0");
        }
        double emb = num(v, "binding.embedding.threshold");
        if (emb < 0.0 || emb > 1.0) {
            throw new ConfigException("binding.embedding.threshold must be in [0, 1]");
        }
        if (!"calibrated-on-DEV".equals(v.get("binding.geometry.tolerance"))) {
            double g = num(v, "binding.geometry.tolerance");
            if (g <= 0.0 || g > 1.0) {
                throw new ConfigException("binding.geometry.tolerance must be in (0, 1] or calibrated-on-DEV");
            }
        }
        long margin = whole(v, "binding.windowMarginMs");
        if (margin < 0 || margin > 60_000L) {
            throw new ConfigException("binding.windowMarginMs must be between 0 and 60000");
        }
        long minLive = whole(v, "passive.minLiveVotes");
        if (minLive < 1 || minLive > 5) {
            throw new ConfigException("passive.minLiveVotes must be between 1 and 5 (there are five voters)");
        }
        String weights = v.get("passive.voterWeights");
        if (!"equal".equals(weights)) {
            String[] parts = weights.split(",", -1);
            if (parts.length > 5) {
                throw new ConfigException("passive.voterWeights has more than five weights");
            }
            for (String p : parts) {
                try {
                    double w = Double.parseDouble(p.trim());
                    if (!(w > 0.0) || Double.isInfinite(w)) {
                        throw new ConfigException("passive.voterWeights must all be positive numbers");
                    }
                } catch (NumberFormatException e) {
                    throw new ConfigException("passive.voterWeights must be 'equal' or comma-separated positive numbers, got '" + p + "'");
                }
            }
        }
        String fp = v.get("frameProcessing");
        if (!"CONTINUOUS".equals(fp) && !"PERIODIC".equals(fp)) {
            throw new ConfigException("frameProcessing must be CONTINUOUS or PERIODIC");
        }
        String acc = v.get("acceleration").toUpperCase(java.util.Locale.ROOT);
        if (!Arrays.asList("AUTO", "CPU", "COREML", "NNAPI", "DIRECTML", "XNNPACK").contains(acc)) {
            throw new ConfigException("acceleration must be AUTO, CPU, COREML, NNAPI, DIRECTML or XNNPACK");
        }
        for (String c : v.get("active.challengeTypes").split(",")) {
            try {
                io.sajeev.api.ChallengeType.valueOf(c.trim());
            } catch (IllegalArgumentException e) {
                throw new ConfigException("active.challengeTypes has an unknown type '" + c.trim() + "'");
            }
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
