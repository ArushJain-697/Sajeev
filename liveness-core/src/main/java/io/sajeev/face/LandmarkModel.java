// #17 (WS1) | Bible §6.2 | MediaPipe face landmarks (478 points incl. iris) via ONNX Runtime. Java 11 only.
// Input: RGB, 256x256, values 0..1 (checked against the Python export). Output 0: 478 x (x, y, z) in 256-px crop space.
package io.sajeev.face;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import io.sajeev.image.PixelImage;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Collections;

/** Runs the landmark model on a square crop around a face box and maps the points back to frame pixels. */
public final class LandmarkModel implements AutoCloseable {

    /** Model input side. */
    static final int SIDE = 256;
    /** Points in the mesh (468) plus iris (10). */
    public static final int POINTS = 478;
    /** Crop side as a multiple of the face box side (room for the forehead and chin). */
    static final double CROP_SCALE = 1.5;

    private final OrtEnvironment env;
    private final OrtSession session;

    /** @param model face_landmarks_detector.onnx @throws OrtException if it cannot load */
    public LandmarkModel(Path model) throws OrtException {
        this.env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(model.toString(), new OrtSession.SessionOptions());
    }

    /**
     * @param frame the full frame
     * @param face the detected face box in frame pixels
     * @return landmarks as float[POINTS][3] in frame pixel coordinates (x, y in frame px; z in model units)
     * @throws OrtException if inference fails
     */
    public float[][] landmarks(PixelImage frame, Detection face) throws OrtException {
        double side = Math.max(face.w(), face.h()) * CROP_SCALE;
        double cx = face.x() + face.w() / 2;
        double cy = face.y() + face.h() / 2;
        double x0 = cx - side / 2;
        double y0 = cy - side / 2;
        int cx0 = clamp(x0, frame.getWidth());
        int cy0 = clamp(y0, frame.getHeight());
        PixelImage crop = frame.crop(cx0, cy0, Math.max(1, Math.min(frame.getWidth() - cx0, (int) side)),
                Math.max(1, Math.min(frame.getHeight() - cy0, (int) side))).resize(SIDE, SIDE);
        // The exported model is NHWC (1 x 256 x 256 x 3), as the TFLite original: RGB, 0..1
        float[] hwc = new float[SIDE * SIDE * 3];
        for (int y = 0; y < SIDE; y++) {
            for (int x = 0; x < SIDE; x++) {
                int rgb = crop.getRGB(x, y);
                int i = (y * SIDE + x) * 3;
                hwc[i] = ((rgb >> 16) & 0xFF) / 255f;
                hwc[i + 1] = ((rgb >> 8) & 0xFF) / 255f;
                hwc[i + 2] = (rgb & 0xFF) / 255f;
            }
        }
        float[] raw;
        try (OnnxTensor in = OnnxTensor.createTensor(env, FloatBuffer.wrap(hwc), new long[] {1, SIDE, SIDE, 3});
             Result out = session.run(Collections.singletonMap(session.getInputNames().iterator().next(), in))) {
            raw = ((float[][][][]) out.get(0).getValue())[0][0][0];
        }
        float[][] pts = new float[POINTS][3];
        double sx = side / SIDE;
        for (int i = 0; i < POINTS; i++) {
            pts[i][0] = (float) (x0 + raw[i * 3] * sx);
            pts[i][1] = (float) (y0 + raw[i * 3 + 1] * sx);
            pts[i][2] = raw[i * 3 + 2];
        }
        return pts;
    }

    private static int clamp(double v, int max) {
        return (int) Math.max(0, Math.min(max - 1, v));
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
