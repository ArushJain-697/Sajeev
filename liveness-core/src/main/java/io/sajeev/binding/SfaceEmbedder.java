// #27 (WS1) | Bible §6.7 | SFace face embedding (OpenCV Zoo, Apache-2.0) via ONNX Runtime. Java 11 only.
// Input: 112x112 aligned crop, RGB, values 0..255 (as OpenCV's FaceRecognizerSF feeds it). Output: 128 numbers.
package io.sajeev.binding;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import io.sajeev.image.PixelImage;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Collections;

/** Turns an aligned face into an embedding and compares two embeddings. */
public final class SfaceEmbedder implements AutoCloseable {

    private final OrtEnvironment env;
    private final OrtSession session;

    /** @param model face_recognition_sface_2021dec.onnx @throws OrtException if it cannot load */
    public SfaceEmbedder(Path model) throws OrtException {
        this.env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(model.toString(), new OrtSession.SessionOptions());
    }

    /**
     * @param aligned a 112x112 crop from {@link FaceAlignment#align}
     * @return the 128-number embedding
     * @throws OrtException if inference fails
     */
    public float[] embed(PixelImage aligned) throws OrtException {
        int side = FaceAlignment.SIDE;
        float[] chw = new float[3 * side * side];
        int plane = side * side;
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                int rgb = aligned.getRGB(x, y);
                int i = y * side + x;
                chw[i] = (rgb >> 16) & 0xFF;
                chw[plane + i] = (rgb >> 8) & 0xFF;
                chw[2 * plane + i] = rgb & 0xFF;
            }
        }
        try (OnnxTensor in = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), new long[] {1, 3, side, side});
             OrtSession.Result out = session.run(Collections.singletonMap(session.getInputNames().iterator().next(), in))) {
            return ((float[][]) out.get(0).getValue())[0];
        }
    }

    /** @return cosine similarity of two embeddings, in [-1, 1] */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return -1.0; // not comparable: the least similar value, never a match
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0.0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (OrtException e) {
            // already closed
        }
    }
}
