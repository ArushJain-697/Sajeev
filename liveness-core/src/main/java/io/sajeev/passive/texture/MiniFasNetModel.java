// #18 (WS1) | Bible §6.3 | One MiniFASNet model on ONNX Runtime. Preprocessing matches the Silent-Face reference
// (crop with scale, resize to 80x80, BGR, values 0..1). Java 11 only.
package io.sajeev.passive.texture;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.Result;
import io.sajeev.face.Detection;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Collections;

/** One MiniFASNet ONNX model. Input 1x3x80x80 (BGR, 0..1). Output: three logits; index 1 means "real". */
public final class MiniFasNetModel implements AutoCloseable {

    /** Input side length of both models. */
    static final int SIDE = 80;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final double scale;

    /**
     * @param model the ONNX file
     * @param scale the crop scale from the model name (2.7 for V2, 4.0 for V1SE)
     * @throws OrtException if the model cannot be loaded
     */
    public MiniFasNetModel(Path model, double scale) throws OrtException {
        this.env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(model.toString(), new OrtSession.SessionOptions());
        this.scale = scale;
    }

    /** @return the crop scale */
    public double scale() {
        return scale;
    }

    /**
     * Raw logits for a prepared CHW tensor (1x3x80x80, BGR, 0..1).
     * @param chw 3*80*80 floats
     * @return the three logits
     * @throws OrtException if inference fails
     */
    public float[] logits(float[] chw) throws OrtException {
        try (OnnxTensor in = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), new long[] {1, 3, SIDE, SIDE});
             Result out = session.run(Collections.singletonMap(session.getInputNames().iterator().next(), in))) {
            float[][] v = (float[][]) out.get(0).getValue();
            return v[0];
        }
    }

    /**
     * Probability that the crop is a real face: softmax over the three logits, index 1.
     * @param image the frame
     * @param face the detected face box in the frame's pixels
     * @return live probability in [0, 1]
     * @throws OrtException if inference fails
     */
    public double liveProbability(BufferedImage image, Detection face) throws OrtException {
        float[] l = logits(toChw(crop(image, face, scale)));
        return softmax(l)[1];
    }

    /**
     * Crop around the face box, expanded by scale, clamped to the frame. Same arithmetic as
     * generate_patches.py _get_new_box, then resized to 80x80 (bilinear).
     */
    static BufferedImage crop(BufferedImage src, Detection face, double scale) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        double boxW = face.w();
        double boxH = face.h();
        double s = Math.min((srcH - 1) / boxH, Math.min((srcW - 1) / boxW, scale));
        double newW = boxW * s;
        double newH = boxH * s;
        double cx = boxW / 2 + face.x();
        double cy = boxH / 2 + face.y();
        double lx = cx - newW / 2;
        double ly = cy - newH / 2;
        double rx = cx + newW / 2;
        double ry = cy + newH / 2;
        if (lx < 0) {
            rx -= lx;
            lx = 0;
        }
        if (ly < 0) {
            ry -= ly;
            ly = 0;
        }
        if (rx > srcW - 1) {
            lx -= rx - srcW + 1;
            rx = srcW - 1;
        }
        if (ry > srcH - 1) {
            ly -= ry - srcH + 1;
            ry = srcH - 1;
        }
        int x0 = (int) lx;
        int y0 = (int) ly;
        int x1 = Math.max(x0 + 1, (int) rx);
        int y1 = Math.max(y0 + 1, (int) ry);
        BufferedImage sub = src.getSubimage(x0, y0, Math.min(x1 + 1, srcW) - x0, Math.min(y1 + 1, srcH) - y0);
        BufferedImage out = new BufferedImage(SIDE, SIDE, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(sub, 0, 0, SIDE, SIDE, null);
        g.dispose();
        return out;
    }

    /** BGR interleaved bytes to CHW floats in 0..1 (ToTensor). */
    static float[] toChw(BufferedImage bgr) {
        byte[] px = ((DataBufferByte) bgr.getRaster().getDataBuffer()).getData();
        int plane = SIDE * SIDE;
        float[] chw = new float[3 * plane];
        for (int i = 0; i < plane; i++) {
            chw[i] = (px[i * 3] & 0xFF) / 255f;
            chw[plane + i] = (px[i * 3 + 1] & 0xFF) / 255f;
            chw[2 * plane + i] = (px[i * 3 + 2] & 0xFF) / 255f;
        }
        return chw;
    }

    static double[] softmax(float[] l) {
        double max = Double.NEGATIVE_INFINITY;
        for (float v : l) {
            max = Math.max(max, v);
        }
        double sum = 0.0;
        double[] e = new double[l.length];
        for (int i = 0; i < l.length; i++) {
            e[i] = Math.exp(l[i] - max);
            sum += e[i];
        }
        for (int i = 0; i < e.length; i++) {
            e[i] /= sum;
        }
        return e;
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
