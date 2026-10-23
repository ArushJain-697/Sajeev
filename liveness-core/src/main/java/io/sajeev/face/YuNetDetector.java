// #16 (WS1) | Bible §6.2 | YuNet face detector via ONNX Runtime (MIT). Java 11 only.
// Decoding follows the YuNet head: anchor-free, strides 8/16/32, score = sqrt(cls * obj).
package io.sajeev.face;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import io.sajeev.image.PixelImage;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Runs YuNet on one image. Create once and reuse (sessions are expensive; see R1-12). */
public final class YuNetDetector implements AutoCloseable {

    /** YuNet's fixed input size. */
    static final int INPUT = 640;
    private static final int[] STRIDES = {8, 16, 32};

    private final OrtEnvironment env;
    private final OrtSession session;

    /**
     * @param model path to face_detection_yunet_2023mar.onnx
     * @throws OrtException if the model cannot be loaded
     */
    public YuNetDetector(Path model) throws OrtException {
        this.env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(model.toString(), new OrtSession.SessionOptions());
    }

    /**
     * Detect faces in an image.
     * @param image a 3-byte BGR or RGB image (any size)
     * @param scoreThreshold keep boxes with score >= this (0.6 in the Python reference)
     * @param nmsIou non-maximum suppression IoU threshold (0.3 in the Python reference)
     * @return boxes in the image's own pixel coordinates, best first
     * @throws OrtException if inference fails
     */
    public List<Detection> detect(PixelImage image, double scoreThreshold, double nmsIou) throws OrtException {
        int W = image.getWidth();
        int H = image.getHeight();
        PixelImage small = image.resize(INPUT, INPUT);
        float[] chw = new float[3 * INPUT * INPUT];
        int plane = INPUT * INPUT;
        for (int i = 0; i < plane; i++) {
            int rgb = small.getRGB(i % INPUT, i / INPUT);
            chw[i] = rgb & 0xFF;                      // B
            chw[plane + i] = (rgb >> 8) & 0xFF;       // G
            chw[2 * plane + i] = (rgb >> 16) & 0xFF;  // R
        }
        float[] raw;
        try (OnnxTensor in = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), new long[] {1, 3, INPUT, INPUT});
             Result out = session.run(Collections.singletonMap(session.getInputNames().iterator().next(), in))) {
            List<float[]> boxes = decode(out, scoreThreshold);
            List<Detection> kept = nms(boxes, nmsIou);
            List<Detection> scaled = new ArrayList<>();
            double sx = (double) W / INPUT;
            double sy = (double) H / INPUT;
            for (Detection d : kept) {
                scaled.add(new Detection(d.x() * sx, d.y() * sy, d.w() * sx, d.h() * sy, d.score()));
            }
            return scaled;
        }
    }

    private static List<float[]> decode(Result out, double thr) throws OrtException {
        List<float[]> boxes = new ArrayList<>();
        for (int stride : STRIDES) {
            float[] cls = flat((float[][][]) out.get("cls_" + stride).get().getValue());
            float[] obj = flat((float[][][]) out.get("obj_" + stride).get().getValue());
            float[] bbox = flat((float[][][]) out.get("bbox_" + stride).get().getValue());
            int cols = INPUT / stride;
            for (int i = 0; i < cls.length; i++) {
                double c = clamp01(cls[i]);
                double o = clamp01(obj[i]);
                double score = Math.sqrt(c * o);
                if (score < thr) {
                    continue;
                }
                int r = i / cols;
                int col = i % cols;
                double cx = (col + bbox[i * 4]) * stride;
                double cy = (r + bbox[i * 4 + 1]) * stride;
                double w = Math.exp(bbox[i * 4 + 2]) * stride;
                double h = Math.exp(bbox[i * 4 + 3]) * stride;
                boxes.add(new float[] {(float) (cx - w / 2), (float) (cy - h / 2), (float) w, (float) h, (float) score});
            }
        }
        return boxes;
    }

    private static List<Detection> nms(List<float[]> boxes, double iouThr) {
        List<float[]> sorted = new ArrayList<>(boxes);
        sorted.sort(Comparator.comparingDouble((float[] b) -> b[4]).reversed());
        List<Detection> keep = new ArrayList<>();
        List<float[]> active = new ArrayList<>(sorted);
        while (!active.isEmpty()) {
            float[] best = active.remove(0);
            keep.add(new Detection(best[0], best[1], best[2], best[3], best[4]));
            active.removeIf(b -> iou(best, b) > iouThr);
        }
        return keep;
    }

    private static double iou(float[] a, float[] b) {
        double x1 = Math.max(a[0], b[0]);
        double y1 = Math.max(a[1], b[1]);
        double x2 = Math.min(a[0] + a[2], b[0] + b[2]);
        double y2 = Math.min(a[1] + a[3], b[1] + b[3]);
        double inter = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        double union = a[2] * a[3] + b[2] * b[3] - inter;
        return union <= 0 ? 0 : inter / union;
    }

    private static float[] flat(float[][][] t) {
        int n = t[0].length;
        int c = t[0][0].length;
        float[] out = new float[n * c];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < c; j++) {
                out[i * c + j] = t[0][i][j];
            }
        }
        return out;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (OrtException e) {
            // closing a session that is already gone is harmless
        }
    }
}
