// #32 (WS1) | Bible §7 | Where verified model versions live on disk, and the only way a new version gets in. Java 11 only.
// The engine itself never touches the network. The client's own sync downloads an update into a staging folder (manifest.txt,
// manifest.sig and the files) when it is online; this class then verifies the whole staging folder offline and installs it.
// Layout:  <root>/v<N>/...   one folder per verified version;  <root>/CURRENT  the number of the version in use.
package io.sajeev.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Installs verified model versions without ever leaving the engine on an unverified or half-copied one. */
public final class ModelStore {

    private final Path root;
    private final ModelVerifier verifier;

    /**
     * @param root the folder that holds the versions
     * @param key the public key that signs manifests
     */
    public ModelStore(Path root, PublicKey key) {
        this.root = root;
        this.verifier = new ModelVerifier(key);
    }

    private Path currentFile() {
        return root.resolve("CURRENT");
    }

    /** @return the version in use, or 0 when none is installed */
    public long currentVersion() throws IOException {
        if (!Files.isRegularFile(currentFile())) {
            return 0;
        }
        return Long.parseLong(new String(Files.readAllBytes(currentFile()), StandardCharsets.UTF_8).trim());
    }

    /** @return the folder of the version in use, or null when none is installed */
    public Path currentDir() throws IOException {
        long v = currentVersion();
        return v == 0 ? null : root.resolve("v" + v);
    }

    /**
     * Verify a staged update and make it the version in use. Nothing changes if anything is wrong: the old version stays in use.
     *
     * @param staging folder with manifest.txt, manifest.sig and the model files
     * @return the installed version
     * @throws ModelVerifier.Refused if the signature, a file hash or size, or the version number (not newer) is wrong
     * @throws IOException if copying fails; the half-copied folder is removed and the old version stays in use
     */
    public long install(Path staging) throws ModelVerifier.Refused, IOException {
        Path mf = staging.resolve("manifest.txt");
        Path sg = staging.resolve("manifest.sig");
        if (!Files.isRegularFile(mf) || !Files.isRegularFile(sg)) {
            throw new ModelVerifier.Refused("the update has no signed manifest");
        }
        long old = currentVersion();
        ModelManifest m = verifier.verify(staging, new String(Files.readAllBytes(mf), StandardCharsets.UTF_8), Files.readAllBytes(sg), old);
        Files.createDirectories(root);
        Path tmp = root.resolve("tmp-" + m.version());
        Path target = root.resolve("v" + m.version());
        deleteTree(tmp);
        try {
            copyTree(staging, tmp);
            // re-verify the copy: what is on disk is what was signed
            verifier.verify(tmp, new String(Files.readAllBytes(tmp.resolve("manifest.txt")), StandardCharsets.UTF_8),
                    Files.readAllBytes(tmp.resolve("manifest.sig")), old);
            deleteTree(target);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            deleteTree(tmp);
            throw e;
        } catch (ModelVerifier.Refused e) {
            deleteTree(tmp);
            throw e;
        }
        writeCurrent(m.version());
        prune(m.version());
        return m.version();
    }

    /** Go back to the newest older version that is still on disk. @return the version now in use, or -1 if there is none */
    public long rollback() throws IOException {
        long cur = currentVersion();
        long best = -1;
        for (long v : versionsOnDisk()) {
            if (v < cur && v > best) {
                best = v;
            }
        }
        if (best > 0) {
            writeCurrent(best);
        }
        return best;
    }

    private List<Long> versionsOnDisk() throws IOException {
        List<Long> out = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (Stream<Path> s = Files.list(root)) {
            for (Path p : s.collect(Collectors.toList())) {
                String n = p.getFileName().toString();
                if (n.matches("v[0-9]+") && Files.isDirectory(p)) {
                    out.add(Long.parseLong(n.substring(1)));
                }
            }
        }
        return out;
    }

    private void writeCurrent(long v) throws IOException {
        Path t = root.resolve("CURRENT.tmp");
        Files.write(t, Long.toString(v).getBytes(StandardCharsets.UTF_8));
        Files.move(t, currentFile(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Keep the version in use and the one before it (the last good version); delete older ones. */
    private void prune(long current) throws IOException {
        List<Long> vs = versionsOnDisk();
        vs.sort(Comparator.reverseOrder());
        int kept = 0;
        for (long v : vs) {
            if (v == current || kept < 1 && v < current) {
                kept += v == current ? 0 : 1;
                continue;
            }
            deleteTree(root.resolve("v" + v));
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : s.collect(Collectors.toList())) {
                Path dest = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) {
            return;
        }
        try (Stream<Path> s = Files.walk(p)) {
            for (Path x : s.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                Files.delete(x);
            }
        }
    }
}
