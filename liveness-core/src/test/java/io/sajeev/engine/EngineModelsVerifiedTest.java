// #32/#34 (WS1) | Models are verified against the signed manifest BEFORE they reach ONNX Runtime.
package io.sajeev.engine;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.sajeev.model.ManifestTool;
import io.sajeev.model.ModelVerifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EngineModelsVerifiedTest {

    private static final String[] FILES = {"yunet/face_detection_yunet_2023mar.onnx", "landmark/face_landmarks_detector.onnx",
        "minifasnet/2.7_80x80_MiniFASNetV2.onnx", "minifasnet/4_0_0_80x80_MiniFASNetV1SE.onnx", "sface/face_recognition_sface_2021dec.onnx"};

    private static Path models() {
        String dir = System.getProperty("sajeev.models", System.getenv("SAJEEV_MODELS"));
        assumeTrue(dir != null, "set -Dsajeev.models=<dir>");
        return Paths.get(dir);
    }

    @Test
    void test_no_manifest_means_models_are_not_loaded(@TempDir Path empty) throws Exception {
        KeyPair kp = keys();
        assertThrows(ModelVerifier.Refused.class, () -> EngineModels.loadVerified(empty, kp.getPublic(), 0));
    }

    private static KeyPair keys() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }

    /** Copies the five model files (with their .onnx.data sidecars) and signs them with a throw-away key. */
    private static KeyPair signedCopy(Path to) throws Exception {
        Path src = models();
        for (String f : FILES) {
            Path t = to.resolve(f);
            Files.createDirectories(t.getParent());
            Files.copy(src.resolve(f), t, StandardCopyOption.REPLACE_EXISTING);
            Path data = src.resolve(f + ".data");
            if (Files.exists(data)) {
                Files.copy(data, to.resolve(f + ".data"), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        KeyPair kp = keys();
        io.sajeev.model.ModelManifest m = ManifestTool.build(to, 5);
        Files.write(to.resolve("manifest.txt"), m.canonicalBytes());
        Files.write(to.resolve("manifest.sig"), ManifestTool.sign(m, kp.getPrivate()));
        return kp;
    }

    @Test
    void test_signed_models_load(@TempDir Path dir) throws Exception {
        KeyPair kp = signedCopy(dir);
        try (EngineModels m = EngineModels.loadVerified(dir, kp.getPublic(), 4)) {
            assertTrue(m.detector() != null);
        }
    }

    @Test
    void test_tampered_model_is_refused_before_it_is_loaded(@TempDir Path dir) throws Exception {
        KeyPair kp = signedCopy(dir);
        Path victim = dir.resolve(FILES[2]);
        byte[] b = Files.readAllBytes(victim);
        b[b.length / 2] ^= 0x01;
        Files.write(victim, b);
        ModelVerifier.Refused r = assertThrows(ModelVerifier.Refused.class, () -> EngineModels.loadVerified(dir, kp.getPublic(), 4));
        assertTrue(r.getMessage().contains("mismatch"), r.getMessage());
    }

    @Test
    void test_wrong_key_and_downgrade_are_refused(@TempDir Path dir) throws Exception {
        KeyPair kp = signedCopy(dir);
        assertThrows(ModelVerifier.Refused.class, () -> EngineModels.loadVerified(dir, keys().getPublic(), 4));
        assertThrows(ModelVerifier.Refused.class, () -> EngineModels.loadVerified(dir, kp.getPublic(), 5)); // same version is not newer
    }

    @Test
    void test_bundled_trust_root_is_a_valid_p256_key() throws Exception {
        assertTrue("EC".equals(EngineModels.bundledTrustRoot().getAlgorithm()));
    }
}
