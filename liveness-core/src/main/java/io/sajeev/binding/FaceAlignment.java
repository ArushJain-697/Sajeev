// #27 (WS1) | Bible §6.7 | 5-point similarity alignment to a 112x112 crop for SFace. Pure Java. Java 11 only.
// The five points come from the MediaPipe landmarks (eye centres from the iris, nose tip, mouth corners).
package io.sajeev.binding;

import io.sajeev.image.PixelImage;

/** Warps a face to the ArcFace 112x112 template. Alignment errors cause false rejects, so the maths is tested. */
public final class FaceAlignment {

    /** ArcFace 112x112 template: image-left eye, image-right eye, nose, image-left mouth corner, image-right mouth corner. */
    static final double[][] TEMPLATE = {
        {38.2946, 51.6963}, {73.5318, 51.5014}, {56.0252, 71.7366}, {41.5493, 92.3655}, {70.7299, 92.2041}};

    /** Output side in pixels. */
    public static final int SIDE = 112;

    private FaceAlignment() {
    }

    /**
     * Five source points from the MediaPipe landmark array, in the template's order.
     * @param p 478 landmarks in frame pixels
     * @return five {x, y} points
     */
    public static double[][] fivePoints(float[][] p) {
        double[][] out = new double[5][];
        out[0] = mean(p, 468, 5); // image-left eye centre (iris)
        out[1] = mean(p, 473, 5); // image-right eye centre (iris)
        out[2] = new double[] {p[1][0], p[1][1]}; // nose tip
        out[3] = new double[] {p[61][0], p[61][1]}; // image-left mouth corner
        out[4] = new double[] {p[291][0], p[291][1]}; // image-right mouth corner
        return out;
    }

    private static double[] mean(float[][] p, int first, int n) {
        double x = 0;
        double y = 0;
        for (int i = 0; i < n; i++) {
            x += p[first + i][0];
            y += p[first + i][1];
        }
        return new double[] {x / n, y / n};
    }

    /**
     * Least-squares similarity transform (scale, rotation, translation) from src to dst (Umeyama).
     * @return {a, b, tx, ty} so that dst = [a -b; b a] * src + [tx, ty]
     */
    static double[] similarity(double[][] src, double[][] dst) {
        int n = src.length;
        double sx = 0, sy = 0, dx = 0, dy = 0;
        for (int i = 0; i < n; i++) {
            sx += src[i][0]; sy += src[i][1]; dx += dst[i][0]; dy += dst[i][1];
        }
        sx /= n; sy /= n; dx /= n; dy /= n;
        double num1 = 0, num2 = 0, den = 0;
        for (int i = 0; i < n; i++) {
            double ax = src[i][0] - sx, ay = src[i][1] - sy, bx = dst[i][0] - dx, by = dst[i][1] - dy;
            num1 += ax * bx + ay * by;   // a * den
            num2 += ax * by - ay * bx;   // b * den
            den += ax * ax + ay * ay;
        }
        double a = num1 / den;
        double b = num2 / den;
        double tx = dx - (a * sx - b * sy);
        double ty = dy - (b * sx + a * sy);
        return new double[] {a, b, tx, ty};
    }

    /**
     * Align a face to 112x112.
     * @param img the frame
     * @param landmarks the 478 landmarks of the single face in the frame
     * @return the aligned crop (RGB)
     */
    public static PixelImage align(PixelImage img, float[][] landmarks) {
        double[] t = similarity(fivePoints(landmarks), TEMPLATE);
        double a = t[0], b = t[1], tx = t[2], ty = t[3];
        double det = a * a + b * b;
        PixelImage out = new PixelImage(SIDE, SIDE);
        for (int v = 0; v < SIDE; v++) {
            for (int u = 0; u < SIDE; u++) {
                // inverse map: src = M^-1 (dst - t)
                double du = u - tx, dv = v - ty;
                double sx = (a * du + b * dv) / det;
                double sy = (-b * du + a * dv) / det;
                out.setRGB(u, v, bilinear(img, sx, sy));
            }
        }
        return out;
    }

    private static int bilinear(PixelImage img, double x, double y) {
        int w = img.getWidth();
        int h = img.getHeight();
        double cx = Math.max(0, Math.min(w - 1.001, x));
        double cy = Math.max(0, Math.min(h - 1.001, y));
        int x0 = (int) cx, y0 = (int) cy;
        double fx = cx - x0, fy = cy - y0;
        int[] c = {img.getRGB(x0, y0), img.getRGB(x0 + 1, y0), img.getRGB(x0, y0 + 1), img.getRGB(x0 + 1, y0 + 1)};
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            double v = ((c[0] >> shift) & 0xFF) * (1 - fx) * (1 - fy) + ((c[1] >> shift) & 0xFF) * fx * (1 - fy)
                    + ((c[2] >> shift) & 0xFF) * (1 - fx) * fy + ((c[3] >> shift) & 0xFF) * fx * fy;
            rgb |= ((int) Math.round(v) & 0xFF) << shift;
        }
        return rgb;
    }
}
