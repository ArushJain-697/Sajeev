// #29 (WS1) | PDF UI §1–§8 | Message keys and the three bundles (EN, FR, AR). Java 11 only.
// The engine returns keys; the client shows the text. Users never see internal names.
package io.sajeev.guidance;

import io.sajeev.api.ErrorState;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Loads the message bundles and maps error states to keys. */
public final class GuidanceKeys {

    /** Languages shipped in this build. */
    public static final String[] LANGS = {"en", "fr", "ar", "hi", "kn", "ta"};

    private static final Map<ErrorState, String> ERROR_KEYS;

    static {
        Map<ErrorState, String> m = new EnumMap<>(ErrorState.class);
        m.put(ErrorState.DEVICE_UNAVAILABLE, "error.device_unavailable");
        m.put(ErrorState.DEVICE_DISCONNECTED, "error.device_disconnected");
        m.put(ErrorState.CAMERA_UNAVAILABLE, "error.camera_unavailable");
        m.put(ErrorState.FACE_NOT_DETECTED, "error.face_not_detected");
        m.put(ErrorState.MULTIPLE_FACES, "error.multiple_faces");
        m.put(ErrorState.POOR_QUALITY, "error.poor_quality_dark");
        m.put(ErrorState.ACTIVE_REQUIRED, "error.active_required");
        m.put(ErrorState.PAD_FAIL, "failure.pad");
        m.put(ErrorState.CHALLENGE_FAILED, "error.challenge_failed");
        m.put(ErrorState.CHALLENGE_TIMEOUT, "error.challenge_timeout");
        m.put(ErrorState.MAX_RETRIES, "failure.max_retries");
        m.put(ErrorState.INVALID_FRAME_DATA, "error.invalid_frame");
        m.put(ErrorState.ENGINE_ERROR, "error.engine");
        ERROR_KEYS = Collections.unmodifiableMap(m);
    }

    private final Map<String, Properties> bundles = new java.util.HashMap<>();

    /** @throws IOException if a bundle is missing from the jar */
    public GuidanceKeys() throws IOException {
        for (String lang : LANGS) {
            Properties p = new Properties();
            try (InputStream in = GuidanceKeys.class.getResourceAsStream("/guidance/messages_" + lang + ".properties")) {
                if (in == null) {
                    throw new IOException("missing bundle for " + lang);
                }
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            bundles.put(lang, p);
        }
    }

    /**
     * The bundled texts plus the operator's own wording (configurable user guidance). {@code overridesDir} holds files named
     * {@code messages_<lang>.properties} (UTF-8) with only the keys to change. A key that does not exist in the bundle, a blank
     * text, or a language we do not ship is rejected with an exception that names it, so a typo is never silently ignored.
     * The value "none" (the default) or a missing folder means no overrides.
     *
     * @param overridesDir folder with the override files, or null
     * @throws IOException if a bundle or an override file cannot be read
     * @throws IllegalArgumentException for an unknown key, a blank text or an unknown language file
     */
    public GuidanceKeys(java.nio.file.Path overridesDir) throws IOException {
        this();
        if (overridesDir == null || !java.nio.file.Files.isDirectory(overridesDir)) {
            return;
        }
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(overridesDir)) {
            for (java.nio.file.Path f : (Iterable<java.nio.file.Path>) files.sorted()::iterator) {
                String name = f.getFileName().toString();
                if (!name.startsWith("messages_") || !name.endsWith(".properties")) {
                    continue;
                }
                String lang = name.substring("messages_".length(), name.length() - ".properties".length());
                if (!bundles.containsKey(lang)) {
                    throw new IllegalArgumentException("override file for a language we do not ship: " + name);
                }
                Properties o = new Properties();
                try (InputStream in = java.nio.file.Files.newInputStream(f)) {
                    o.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
                for (String k : o.stringPropertyNames()) {
                    if (!bundles.get(lang).containsKey(k)) {
                        throw new IllegalArgumentException(name + ": unknown key " + k);
                    }
                    if (o.getProperty(k).trim().isEmpty()) {
                        throw new IllegalArgumentException(name + ": blank text for " + k);
                    }
                    bundles.get(lang).setProperty(k, o.getProperty(k));
                }
            }
        }
    }

    /** @param configValue the value of the guidance.overrides key @return the keys object using that folder ("none" or blank: no overrides) */
    public static GuidanceKeys fromConfig(String configValue) throws IOException {
        boolean none = configValue == null || configValue.trim().isEmpty() || "none".equalsIgnoreCase(configValue.trim());
        return new GuidanceKeys(none ? null : java.nio.file.Paths.get(configValue.trim()));
    }

    /** @param error a PDF error state @return the message key for it (never null) */
    public static String keyFor(ErrorState error) {
        return ERROR_KEYS.get(error);
    }

    /**
     * @param key a message key
     * @param lang "en", "fr" or "ar"
     * @return the text, or the English text if the language lacks the key (never a blank)
     */
    public String text(String key, String lang) {
        String t = bundles.containsKey(lang) ? bundles.get(lang).getProperty(key) : null;
        return t != null ? t : bundles.get("en").getProperty(key);
    }

    /** @param lang a language code @return true for right-to-left languages */
    public static boolean isRtl(String lang) {
        return Locale.forLanguageTag(lang).getLanguage().equals("ar");
    }

    /** @return the key set of one language (for tests) */
    public java.util.Set<String> keys(String lang) {
        return bundles.get(lang).stringPropertyNames();
    }
}
