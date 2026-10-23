// #32 (WS1) | Bible §7, §16 | Signed model manifest: model ids, version, SHA-256 and size per file. Java 11 only.
// The signature covers the whole canonical text, not each entry. The format is plain text on purpose (no JSON
// library in the engine, and a canonical form is easy to sign).
package io.sajeev.model;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** One model's entry in the manifest. */
final class Entry {
    final String sha256Hex;
    final long size;

    Entry(String sha256Hex, long size) {
        this.sha256Hex = sha256Hex;
        this.size = size;
    }
}

/** The manifest: a version and the files it covers. */
public final class ModelManifest {

    private final long version;
    private final Map<String, Entry> files;

    ModelManifest(long version, Map<String, Entry> files) {
        this.version = version;
        this.files = Collections.unmodifiableMap(new TreeMap<>(files));
    }

    /** @return the manifest version; a newer version is always greater */
    public long version() {
        return version;
    }

    /** @return true if the signed manifest lists this file */
    public boolean covers(String name) {
        return files.containsKey(name);
    }

    /** @return file name to entry, sorted */
    Map<String, Entry> files() {
        return files;
    }

    /**
     * Canonical text that is signed. Lines: "manifest-version N", then one "file NAME SHA256 SIZE" per file, sorted.
     * @return the bytes to sign and verify
     */
    public byte[] canonicalBytes() {
        StringBuilder sb = new StringBuilder();
        sb.append("manifest-version ").append(version).append('\n');
        for (Map.Entry<String, Entry> e : files.entrySet()) {
            sb.append("file ").append(e.getKey()).append(' ').append(e.getValue().sha256Hex)
                    .append(' ').append(e.getValue().size).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Parse the canonical text (as produced by canonicalBytes).
     * @param text the manifest text
     * @return the manifest
     * @throws IllegalArgumentException on any malformed line
     */
    public static ModelManifest parse(String text) {
        String[] lines = text.split("\n");
        if (lines.length == 0 || !lines[0].startsWith("manifest-version ")) {
            throw new IllegalArgumentException("missing manifest-version line");
        }
        long v = Long.parseLong(lines[0].substring("manifest-version ".length()).trim());
        Map<String, Entry> files = new TreeMap<>();
        for (int i = 1; i < lines.length; i++) {
            String[] p = lines[i].split(" ");
            if (p.length != 4 || !"file".equals(p[0])) {
                throw new IllegalArgumentException("bad line: " + lines[i]);
            }
            files.put(p[1], new Entry(p[2], Long.parseLong(p[3])));
        }
        return new ModelManifest(v, files);
    }
}
