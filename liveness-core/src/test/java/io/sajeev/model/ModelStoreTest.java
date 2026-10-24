// #32 (WS1) | A model update arrives only through verification; the old version stays in use unless the new one fully passes.
package io.sajeev.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelStoreTest {

    @TempDir Path tmp;
    private KeyPair kp;
    private ModelStore store;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        kp = g.generateKeyPair();
        store = new ModelStore(tmp.resolve("store"), kp.getPublic());
    }

    /** A staged update of one fake model file whose content names the version. */
    private Path stage(String name, long version, String content) throws Exception {
        Path d = tmp.resolve(name);
        Files.createDirectories(d);
        Files.write(d.resolve("a.onnx"), content.getBytes(StandardCharsets.UTF_8));
        ModelManifest m = ManifestTool.build(d, version);
        Files.write(d.resolve("manifest.txt"), m.canonicalBytes());
        Files.write(d.resolve("manifest.sig"), ManifestTool.sign(m, kp.getPrivate()));
        return d;
    }

    private String content(long v) throws Exception {
        return new String(Files.readAllBytes(store.currentDir().resolve("a.onnx")), StandardCharsets.UTF_8);
    }

    @Test
    void test_nothing_is_installed_at_first() throws Exception {
        assertEquals(0, store.currentVersion());
        assertNull(store.currentDir());
    }

    @Test
    void test_a_signed_update_becomes_the_version_in_use() throws Exception {
        assertEquals(1, store.install(stage("u1", 1, "model one")));
        assertEquals("model one", content(1));
        assertEquals(2, store.install(stage("u2", 2, "model two")));
        assertEquals(2, store.currentVersion());
        assertEquals("model two", content(2));
    }

    @Test
    void test_last_good_version_is_kept_after_bad_update() throws Exception {
        store.install(stage("u1", 1, "model one"));
        Path bad = stage("u2", 2, "model two");
        Files.write(bad.resolve("a.onnx"), "tampered".getBytes(StandardCharsets.UTF_8));
        assertThrows(ModelVerifier.Refused.class, () -> store.install(bad));
        assertEquals(1, store.currentVersion());
        assertEquals("model one", content(1));
        assertFalse(Files.exists(tmp.resolve("store/tmp-2")), "no half-installed folder is left");
    }

    @Test
    void test_manifest_downgrade_and_replay_are_rejected() throws Exception {
        store.install(stage("u3", 3, "model three"));
        assertThrows(ModelVerifier.Refused.class, () -> store.install(stage("u2", 2, "old model")));
        assertThrows(ModelVerifier.Refused.class, () -> store.install(stage("u3b", 3, "same version again")));
        assertEquals(3, store.currentVersion());
    }

    @Test
    void test_an_update_signed_by_another_key_or_unsigned_is_refused() throws Exception {
        store.install(stage("u1", 1, "model one"));
        Path d = stage("u2", 2, "model two");
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        ModelManifest m = ModelManifest.parse(new String(Files.readAllBytes(d.resolve("manifest.txt")), StandardCharsets.UTF_8));
        Files.write(d.resolve("manifest.sig"), ManifestTool.sign(m, g.generateKeyPair().getPrivate()));
        assertThrows(ModelVerifier.Refused.class, () -> store.install(d));
        Files.delete(d.resolve("manifest.sig"));
        assertThrows(ModelVerifier.Refused.class, () -> store.install(d));
        assertEquals(1, store.currentVersion());
    }

    @Test
    void test_rollback_returns_to_the_previous_good_version_and_old_ones_are_pruned() throws Exception {
        store.install(stage("u1", 1, "model one"));
        store.install(stage("u2", 2, "model two"));
        store.install(stage("u3", 3, "model three"));
        assertTrue(Files.isDirectory(tmp.resolve("store/v2")), "the last good version is kept");
        assertFalse(Files.exists(tmp.resolve("store/v1")), "older versions are pruned");
        assertEquals(2, store.rollback());
        assertEquals("model two", content(2));
        assertEquals(-1, store.rollback());
    }
}
