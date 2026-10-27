// Red-team 2 fix (07 C2) | What the freeze hash records about the engine code: the content of the tracked engine sources and
// whether the working tree has edits. The git HEAD is NOT used (committing eval/FROZEN would change it). Java 11 only.
package io.sajeev.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The engine sources as git sees them. */
public final class SourceState {

    /** Paths (relative to the sajeev root) whose content decides what the engine does. */
    static final List<String> PATHS = Arrays.asList("liveness-core/src/main", "liveness-core/pom.xml", "liveness-api/src/main",
            "liveness-api/pom.xml", "pom.xml");

    private SourceState() {
    }

    private static String git(Path root, String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(Arrays.asList("git", "-C", root.toString()));
        cmd.addAll(Arrays.asList(args));
        cmd.add("--");
        cmd.addAll(PATHS);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) {
            throw new IOException("git failed: " + out.trim());
        }
        return out;
    }

    /**
     * @param root the sajeev folder (the one with liveness-core and liveness-api)
     * @return "sources=&lt;sha256 of the tracked blobs&gt; tree=clean|dirty", or "sources=unknown tree=unknown" when git cannot say
     */
    public static String describe(Path root) {
        try {
            String blobs = git(root, "ls-files", "-s");
            String status = git(root, "status", "--porcelain", "--untracked-files=all");
            return "sources=" + HeldOutGate.sha256(blobs.getBytes(StandardCharsets.UTF_8)) + " tree=" + (status.trim().isEmpty() ? "clean" : "dirty");
        } catch (IOException e) {
            return "sources=unknown tree=unknown";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "sources=unknown tree=unknown";
        }
    }

    /** @param state a value from {@link #describe} @return true only for a known, clean tree */
    public static boolean isClean(String state) {
        return state.endsWith("tree=clean") && !state.contains("sources=unknown");
    }
}
