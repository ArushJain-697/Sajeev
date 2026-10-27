// #62 (WS4) | The hash that eval/FROZEN records: every default configuration value, the engine tuning settings, the model files
// and the content of the engine sources (not the git HEAD) with a clean/dirty flag. If any of them changes after the freeze, the held-out run refuses. Java 11 only.
package io.sajeev.eval;

import io.sajeev.engine.EngineSettings;
import io.sajeev.policy.Config;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/** Computes the freeze hash; also the command line for eval/freeze.sh. */
public final class FreezeHash {

    private FreezeHash() {
    }

    /**
     * @param models the model directory (every file is hashed), or null to leave models out
     * @param gitCommit the engine source state (from {@link SourceState#describe}: content hash and clean/dirty; never the HEAD)
     * @return a SHA-256 over a canonical text of configuration, settings, models and engine source state
     * @throws IOException on read errors
     */
    public static String compute(Path models, String gitCommit) throws IOException {
        StringBuilder t = new StringBuilder();
        for (Map.Entry<String, String> e : new TreeMap<>(Config.DEFAULTS).entrySet()) {
            t.append("config ").append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        EngineSettings s = EngineSettings.defaults();
        Map<String, String> fields = new TreeMap<>();
        try {
            for (Field f : EngineSettings.class.getFields()) {
                fields.put(f.getName(), String.valueOf(f.get(s)));
            }
        } catch (IllegalAccessException e) {
            throw new IOException(e);
        }
        for (Map.Entry<String, String> e : fields.entrySet()) {
            t.append("setting ").append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        if (models != null) {
            for (String l : HeldOutGate.listing(models)) {
                t.append("model ").append(l).append('\n');
            }
        }
        t.append("engine ").append(gitCommit).append('\n');
        return HeldOutGate.sha256(t.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Usage: FreezeHash &lt;models-dir&gt; &lt;sajeev-root&gt; [out-file]; prints the hash and, with an out-file, writes eval/FROZEN.
     * The engine part of the hash is the content of the tracked engine sources plus a clean/dirty flag (see {@link SourceState}),
     * not the git HEAD. A dirty tree cannot be frozen.
     */
    public static void main(String[] a) throws IOException {
        String state = SourceState.describe(java.nio.file.Paths.get(a[1]).toAbsolutePath());
        if (!SourceState.isClean(state)) {
            System.err.println("REFUSED: the engine sources have uncommitted changes or git cannot be read (" + state
                    + "). Commit first, then freeze.");
            System.exit(3);
        }
        String h = compute(java.nio.file.Paths.get(a[0]), state);
        System.out.println(h);
        if (a.length > 2) {
            Files.write(java.nio.file.Paths.get(a[2]), (h + "  frozen " + java.time.Instant.now() + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }
}
