// #16 (WS1) | Bible §6.2, PDF M9 | Face size, brightness and sharpness on the detected face. Java 11 only.
// Thresholds are constructor parameters, not literals. Their values are TARGET until calibrated on DEV (#62).
package io.sajeev.face;

import java.awt.image.BufferedImage;

/** Measures a frame's face quality. Pure Java: no OpenCV, same code on desktop and Android (D23). */
public final class FaceQuality {

    /** Measured values for one frame. */
    public static final class Metrics {
        private final double faceShare;
        private final double brightness;
        private final double sharpness;

        Metrics(double faceShare, double brightness, double sharpness) {
            this.faceShare = faceShare;
            this.brightness = brightness;
            this.sharpness = sharpness;
        }

        /** @return the face box area divided by the frame area, in [0, 1] */
        public double faceShare() {
            return faceShare;
        }

        /** @return mean grey level of the face crop, 0 to 255 */
        public double brightness() {
            return brightness;
        }

        /** @return variance of the Laplacian of the face crop (higher = sharper) */
        public double sharpness() {
            return sharpness;
        }
    }

    private FaceQuality() {
    }

    /**
     * @param image the frame
     * @param box the detected face, or null when there is no face (then the whole frame is measured)
     * @return the metrics
     */
    public static Metrics measure(BufferedImage image, Detection box) {
        int x0 = 0;
        int y0 = 0;
        int x1 = image.getWidth();
        int y1 = image.getHeight();
        double share = 0.0;
        if (box != null) {
            x0 = clamp((int) Math.round(box.x()), 0, image.getWidth() - 1);
            y0 = clamp((int) Math.round(box.y()), 0, image.getHeight() - 1);
            x1 = clamp((int) Math.round(box.x() + box.w()), x0 + 1, image.getWidth());
            y1 = clamp((int) Math.round(box.y() + box.h()), y0 + 1, image.getHeight());
            share = (box.w() * box.h()) / ((double) image.getWidth() * image.getHeight());
        }
        int w = x1 - x0;
        int h = y1 - y0;
        double[] gray = new double[w * h];
        double sum = 0.0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x0 + x, y0 + y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                double v = 0.299 * r + 0.587 * g + 0.114 * b;
                gray[y * w + x] = v;
                sum += v;
            }
        }
        double mean = sum / gray.length;
        // Laplacian (4-neighbour) variance over the interior pixels
        double lapSum = 0.0;
        double lapSq = 0.0;
        int n = 0;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                double lap = 4 * gray[y * w + x] - gray[y * w + x - 1] - gray[y * w + x + 1]
                        - gray[(y - 1) * w + x] - gray[(y + 1) * w + x];
                lapSum += lap;
                lapSq += lap * lap;
                n++;
            }
        }
        double sharp = 0.0;
        if (n > 0) {
            double lapMean = lapSum / n;
            sharp = lapSq / n - lapMean * lapMean;
        }
        return new Metrics(share, mean, sharp);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
