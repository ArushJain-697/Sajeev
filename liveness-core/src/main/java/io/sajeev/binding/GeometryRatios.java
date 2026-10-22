// #27 (WS1) | Bible §6.7 | Landmark-geometry ratios that do not depend on an identity model. Pure Java. Java 11 only.
package io.sajeev.binding;

/** Scale-free face proportions, used as the second binding signal (red-team R1-6). */
public final class GeometryRatios {

    private GeometryRatios() {
    }

    /**
     * @param p 478 MediaPipe landmarks
     * @return {inter-ocular / face width, eye-to-mouth / face height, face width / face height}
     */
    public static double[] of(float[][] p) {
        double[] l = mean(p, 468);
        double[] r = mean(p, 473);
        double interOcular = Math.hypot(l[0] - r[0], l[1] - r[1]);
        double faceWidth = Math.abs(p[454][0] - p[234][0]);
        double faceHeight = Math.hypot(p[10][0] - p[152][0], p[10][1] - p[152][1]);
        double eyeX = (l[0] + r[0]) / 2.0;
        double eyeY = (l[1] + r[1]) / 2.0;
        double mouthX = (p[13][0] + p[14][0]) / 2.0;
        double mouthY = (p[13][1] + p[14][1]) / 2.0;
        double eyeToMouth = Math.hypot(eyeX - mouthX, eyeY - mouthY);
        if (faceWidth <= 0 || faceHeight <= 0) {
            return new double[] {0, 0, 0};
        }
        return new double[] {interOcular / faceWidth, eyeToMouth / faceHeight, faceWidth / faceHeight};
    }

    /** @return the largest relative difference between two ratio vectors, as a fraction (0.05 = 5%) */
    public static double maxRelativeDifference(double[] a, double[] b) {
        double worst = 0;
        for (int i = 0; i < a.length; i++) {
            double base = Math.max(Math.abs(b[i]), 1e-9);
            worst = Math.max(worst, Math.abs(a[i] - b[i]) / base);
        }
        return worst;
    }

    private static double[] mean(float[][] p, int first) {
        double x = 0;
        double y = 0;
        for (int i = 0; i < 5; i++) {
            x += p[first + i][0];
            y += p[first + i][1];
        }
        return new double[] {x / 5, y / 5};
    }
}
