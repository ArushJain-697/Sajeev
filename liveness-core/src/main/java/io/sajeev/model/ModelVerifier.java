// #32 (WS1) | Bible §7, §16 | Verifies a signed model set before ONNX Runtime loads it. Java 11 only.
// ECDSA P-256 (SHA256withECDSA), not Ed25519: Ed25519 is missing on JDK 11, which the desktop client runs on (D43).
package io.sajeev.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Map;

/** Verifies the manifest signature, every file's SHA-256 and size, and that the version is not a downgrade. */
public final class ModelVerifier {

    /** Thrown when a model set must not be loaded. The message says why; the caller keeps the last good version. */
    public static final class Refused extends Exception {
        private static final long serialVersionUID = 1L;

        /** @param reason why it was refused */
        public Refused(String reason) {
            super(reason);
        }
    }

    private final PublicKey key;

    /** @param key the public key that signs manifests (ECDSA P-256) */
    public ModelVerifier(PublicKey key) {
        this.key = key;
    }

    /**
     * Verify a model set. Call this BEFORE loading anything into ONNX Runtime.
     * @param dir folder with the model files
     * @param manifestText the manifest text (canonical form)
     * @param signature the DER signature over the canonical bytes
     * @param lastGoodVersion the version currently in use; the new one must be higher
     * @return the verified manifest
     * @throws Refused with the reason
     */
    public ModelManifest verify(Path dir, String manifestText, byte[] signature, long lastGoodVersion) throws Refused {
        ModelManifest m;
        try {
            m = ModelManifest.parse(manifestText);
        } catch (RuntimeException e) {
            throw new Refused("manifest unreadable");
        }
        if (!checkSignature(m.canonicalBytes(), signature)) {
            throw new Refused("signature does not verify");
        }
        if (m.version() <= lastGoodVersion) {
            throw new Refused("version " + m.version() + " is not newer than " + lastGoodVersion + " (downgrade)");
        }
        for (Map.Entry<String, Entry> e : m.files().entrySet()) {
            Path f = dir.resolve(e.getKey());
            try {
                if (Files.size(f) != e.getValue().size) {
                    throw new Refused("size mismatch: " + e.getKey());
                }
                if (!sha256Hex(f).equals(e.getValue().sha256Hex)) {
                    throw new Refused("hash mismatch: " + e.getKey());
                }
            } catch (IOException ex) {
                throw new Refused("missing file: " + e.getKey());
            }
        }
        return m;
    }

    private boolean checkSignature(byte[] data, byte[] sig) {
        try {
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initVerify(key);
            s.update(data);
            return s.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }

    static String sha256Hex(Path f) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Files.readAllBytes(f));
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest()) {
                hex.append(String.format("%02x", b & 0xFF));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
