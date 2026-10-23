// #62/#63 (WS4) | HELDOUT_PROTOCOL §4: the checks that must pass before the held-out clips are evaluated. Java 11 only.
// Refuses unless: the thresholds are frozen (eval/FROZEN exists and matches the current configuration hash), the held-out
// folder still matches its seal (HELDOUT.sha256), and this is the first held-out run since sealing (a later run needs a
// reason, which is written to eval/CHANGELOG.md). Every run is appended to HELDOUT_ACCESS.log.
package io.sajeev.eval;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Stateless checks over files in the eval directory. */
public final class HeldOutGate {

    private HeldOutGate() {
    }

    /** The held-out evaluation must not start. */
    public static final class Refused extends Exception {
        private static final long serialVersionUID = 1L;

        public Refused(String m) {
            super(m);
        }
    }

    static String sha256(byte[] b) throws IOException {
        try {
            StringBuilder s = new StringBuilder();
            for (byte x : MessageDigest.getInstance("SHA-256").digest(b)) {
                s.append(String.format("%02x", x));
            }
            return s.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /** @return "sha256  relative path" lines for every file (not .DS_Store), sorted by path: the same format heldout.sh seals */
    static List<String> listing(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            List<Path> files = s.filter(Files::isRegularFile).filter(p -> !p.getFileName().toString().equals(".DS_Store"))
                    .sorted((a, b) -> dir.relativize(a).toString().compareTo(dir.relativize(b).toString()))
                    .collect(Collectors.toList());
            List<String> out = new ArrayList<>();
            for (Path f : files) {
                out.add(sha256(Files.readAllBytes(f)) + "  " + dir.relativize(f));
            }
            return out;
        }
    }

    /**
     * @param evalDir the eval directory (contains data/heldout, HELDOUT.sha256, FROZEN, HELDOUT_ACCESS.log, CHANGELOG.md)
     * @param currentConfigHash the hash of the configuration, models and engine as they are now (see {@link FreezeHash})
     * @param rerunReason null for the first run; a non-blank reason for any later run
     * @throws Refused when a rule is broken
     * @throws IOException on read errors
     */
    public static void check(Path evalDir, String currentConfigHash, String rerunReason) throws Refused, IOException {
        Path frozen = evalDir.resolve("FROZEN");
        if (!Files.isRegularFile(frozen)) {
            throw new Refused("eval/FROZEN is missing: freeze the thresholds before the held-out set is opened");
        }
        String frozenHash = new String(Files.readAllBytes(frozen), StandardCharsets.UTF_8).trim().split("\\s+")[0];
        if (!frozenHash.equals(currentConfigHash)) {
            throw new Refused("the configuration changed after the freeze (FROZEN " + frozenHash.substring(0, 12) + " vs now "
                    + currentConfigHash.substring(0, 12) + ")");
        }
        Path seal = evalDir.resolve("HELDOUT.sha256");
        if (!Files.isRegularFile(seal)) {
            throw new Refused("the held-out set was never sealed (HELDOUT.sha256 missing)");
        }
        Path dir = evalDir.resolve("data").resolve("heldout");
        if (!Files.isDirectory(dir) || !Files.isReadable(dir)) {
            throw new Refused("the held-out folder is locked or missing: unseal it first with heldout.sh");
        }
        List<String> expect = new ArrayList<>();
        for (String l : Files.readAllLines(seal, StandardCharsets.UTF_8)) {
            if (!l.startsWith("FILES ") && !l.startsWith("ROOT ") && !l.startsWith("SEALED_AT ") && !l.isEmpty()) {
                expect.add(l);
            }
        }
        if (!expect.equals(listing(dir))) {
            throw new Refused("the held-out files changed after sealing (hashes do not match the seal)");
        }
        Path log = evalDir.resolve("HELDOUT_ACCESS.log");
        int runsSinceSeal = 0;
        if (Files.isRegularFile(log)) {
            for (String l : Files.readAllLines(log, StandardCharsets.UTF_8)) {
                if (l.contains("  SEALED  ")) {
                    runsSinceSeal = 0;
                } else if (l.contains("  HELDOUT_RUN  ")) {
                    runsSinceSeal++;
                }
            }
        }
        if (runsSinceSeal >= 1) {
            if (rerunReason == null || rerunReason.trim().isEmpty()) {
                throw new Refused("the held-out set was already evaluated since sealing; a rerun needs --rerun \"<reason>\"");
            }
            Files.write(evalDir.resolve("CHANGELOG.md"), ("- " + Instant.now() + " held-out RERUN: " + rerunReason.trim() + "\n")
                    .getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        }
        Files.write(log, (Instant.now() + "  HELDOUT_RUN  user=" + System.getProperty("user.name") + "  reason=\""
                + (rerunReason == null ? "first run" : rerunReason.trim()) + "\"\n").getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }
}
