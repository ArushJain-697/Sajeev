// #34 (WS1) | Bible §6 | The models, loaded once and shared by every session (singletons). Closed on shutdown. Java 11 only.
package io.sajeev.engine;

import ai.onnxruntime.OrtException;
import io.sajeev.binding.SfaceEmbedder;
import io.sajeev.face.LandmarkModel;
import io.sajeev.face.YuNetDetector;
import io.sajeev.model.ModelManifest;
import io.sajeev.model.ModelVerifier;
import io.sajeev.passive.texture.MiniFasNetModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

/** Owns the ONNX sessions. The ONNX Runtime sessions are safe to call from several threads. */
public final class EngineModels implements AutoCloseable {

    final YuNetDetector detector;
    final LandmarkModel landmarks;
    final MiniFasNetModel minifasV2;
    final MiniFasNetModel minifasV1se;
    final SfaceEmbedder sface;

    /** The dev trust root shipped with the engine (the public half of the key that signs the model manifest). */
    public static PublicKey bundledTrustRoot() throws IOException {
        try (java.io.InputStream in = EngineModels.class.getResourceAsStream("/model-signing-public.der")) {
            if (in == null) {
                throw new IOException("trust root missing from the engine jar");
            }
            return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(in.readAllBytes()));
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    /**
     * Verify the signed manifest (manifest.txt and manifest.sig in {@code dir}) against {@code key} and load the models only if it
     * passes: every file the manifest lists (including the .onnx.data files) must have the signed size and SHA-256, the
     * signature must verify and the version must be newer than {@code lastGoodVersion}. Nothing reaches ONNX Runtime before that.
     *
     * @param dir the model directory
     * @param key the public key that signs manifests
     * @param lastGoodVersion the manifest version in use before (0 if none)
     * @return the loaded models
     * @throws ModelVerifier.Refused if the manifest, the signature or any file is not acceptable
     * @throws OrtException if a verified model still cannot load
     * @throws IOException if the manifest files cannot be read
     */
    public static EngineModels loadVerified(Path dir, PublicKey key, long lastGoodVersion)
            throws ModelVerifier.Refused, OrtException, IOException {
        Path mf = dir.resolve("manifest.txt");
        Path sg = dir.resolve("manifest.sig");
        if (!Files.isRegularFile(mf) || !Files.isRegularFile(sg)) {
            throw new ModelVerifier.Refused("no signed manifest in " + dir + ": models are not loaded unverified");
        }
        ModelManifest m = new ModelVerifier(key).verify(dir, new String(Files.readAllBytes(mf), java.nio.charset.StandardCharsets.UTF_8),
                Files.readAllBytes(sg), lastGoodVersion);
        for (String need : new String[] {"yunet/face_detection_yunet_2023mar.onnx", "landmark/face_landmarks_detector.onnx",
            "minifasnet/2.7_80x80_MiniFASNetV2.onnx", "minifasnet/4_0_0_80x80_MiniFASNetV1SE.onnx", "sface/face_recognition_sface_2021dec.onnx"}) {
            if (!m.covers(need)) {
                throw new ModelVerifier.Refused("the signed manifest does not cover " + need);
            }
        }
        return new EngineModels(dir);
    }

    /**
     * @param dir the model directory (yunet/, landmark/, minifasnet/, sface/). In production the signed manifest
     *            check (ModelVerifier) runs on these files before this constructor is called.
     * @throws OrtException if a model cannot load
     */
    public EngineModels(Path dir) throws OrtException {
        detector = new YuNetDetector(dir.resolve("yunet/face_detection_yunet_2023mar.onnx"));
        landmarks = new LandmarkModel(dir.resolve("landmark/face_landmarks_detector.onnx"));
        minifasV2 = new MiniFasNetModel(dir.resolve("minifasnet/2.7_80x80_MiniFASNetV2.onnx"), 2.7);
        minifasV1se = new MiniFasNetModel(dir.resolve("minifasnet/4_0_0_80x80_MiniFASNetV1SE.onnx"), 4.0);
        sface = new SfaceEmbedder(dir.resolve("sface/face_recognition_sface_2021dec.onnx"));
    }

    /** @return the shared face detector (for adapters that run a single-image check) */
    public YuNetDetector detector() {
        return detector;
    }

    public io.sajeev.face.LandmarkModel landmarks() {
        return landmarks;
    }

    public io.sajeev.passive.texture.MiniFasNetModel minifasV2() {
        return minifasV2;
    }

    public io.sajeev.passive.texture.MiniFasNetModel minifasV1se() {
        return minifasV1se;
    }

    @Override
    public void close() {
        detector.close();
        landmarks.close();
        minifasV2.close();
        minifasV1se.close();
        sface.close();
    }
}
