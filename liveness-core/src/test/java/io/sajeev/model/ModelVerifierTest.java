// #32 (WS1) | Tests named as claims. Keys are generated per test run; no key material is stored.
package io.sajeev.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelVerifierTest {

    @TempDir
    Path dir;

    private KeyPair keys(String label) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }

    /** Manifest text for the files currently on disk. */
    private String manifest(long version, String... names) throws Exception {
        StringBuilder sb = new StringBuilder("manifest-version " + version + "\n");
        for (String n : names) {
            Path f = dir.resolve(n);
            sb.append("file ").append(n).append(' ').append(ModelVerifier.sha256Hex(f))
                    .append(' ').append(Files.size(f)).append('\n');
        }
        return sb.toString();
    }

    private static byte[] sign(KeyPair k, String text) throws Exception {
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(k.getPrivate());
        s.update(ModelManifest.parse(text).canonicalBytes());
        return s.sign();
    }

    private void write(String name, String content) throws Exception {
        Files.write(dir.resolve(name), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void test_tampered_model_is_refused() throws Exception {
        KeyPair k = keys("t");
        write("a.onnx", "model-a");
        String text = manifest(1, "a.onnx");
        byte[] sig = sign(k, text);
        write("a.onnx", "model-X"); // same length, changed bytes
        ModelVerifier v = new ModelVerifier(k.getPublic());
        assertThrows(ModelVerifier.Refused.class, () -> v.verify(dir, text, sig, 0));
    }

    @Test
    void test_last_good_version_is_kept_after_bad_update() throws Exception {
        KeyPair k = keys("g");
        write("a.onnx", "good");
        ModelVerifier v = new ModelVerifier(k.getPublic());
        String v2 = manifest(2, "a.onnx");
        long current = v.verify(dir, v2, sign(k, v2), 0).version();
        assertEquals(2L, current);
        // bad update: version 3 is correctly signed for the good bytes, then the file is changed on disk
        String v3 = manifest(3, "a.onnx");
        byte[] sig3 = sign(k, v3);
        write("a.onnx", "evil");
        assertThrows(ModelVerifier.Refused.class, () -> v.verify(dir, v3, sig3, current));
        assertEquals(2L, current, "the engine keeps version 2 after a refused update");
    }

    @Test
    void test_manifest_downgrade_is_rejected() throws Exception {
        KeyPair k = keys("d");
        write("a.onnx", "old");
        String v1 = manifest(1, "a.onnx");
        ModelVerifier v = new ModelVerifier(k.getPublic());
        assertThrows(ModelVerifier.Refused.class, () -> v.verify(dir, v1, sign(k, v1), 2));
    }

    @Test
    void test_unsigned_manifest_is_refused() throws Exception {
        KeyPair k = keys("u");
        write("a.onnx", "x");
        String text = manifest(1, "a.onnx");
        ModelVerifier v = new ModelVerifier(k.getPublic());
        assertThrows(ModelVerifier.Refused.class, () -> v.verify(dir, text, new byte[] {1, 2, 3}, 0));
    }

    @Test
    void test_valid_signed_model_is_accepted() throws Exception {
        KeyPair k = keys("ok");
        write("a.onnx", "fine");
        String text = manifest(5, "a.onnx");
        ModelVerifier v = new ModelVerifier(k.getPublic());
        assertEquals(5L, v.verify(dir, text, sign(k, text), 4).version());
    }
}
