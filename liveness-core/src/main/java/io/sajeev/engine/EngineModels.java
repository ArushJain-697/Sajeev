// #34 (WS1) | Bible §6 | The models, loaded once and shared by every session (singletons). Closed on shutdown. Java 11 only.
package io.sajeev.engine;

import ai.onnxruntime.OrtException;
import io.sajeev.binding.SfaceEmbedder;
import io.sajeev.face.LandmarkModel;
import io.sajeev.face.YuNetDetector;
import io.sajeev.passive.texture.MiniFasNetModel;

import java.nio.file.Path;

/** Owns the ONNX sessions. The ONNX Runtime sessions are safe to call from several threads. */
public final class EngineModels implements AutoCloseable {

    final YuNetDetector detector;
    final LandmarkModel landmarks;
    final MiniFasNetModel minifasV2;
    final MiniFasNetModel minifasV1se;
    final SfaceEmbedder sface;

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

    @Override
    public void close() {
        detector.close();
        landmarks.close();
        minifasV2.close();
        minifasV1se.close();
        sface.close();
    }
}
