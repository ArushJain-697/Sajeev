// #20 (WS1) | Bible §6.4 V3 | Flatness voter: does the face move like one flat plane? Java 11 only.
// Method: choose the two frames with the biggest yaw change (at least minYawChange), fit a homography between ~34 mesh
// landmarks with RANSAC (fixed seed), and measure the leftover error. A flat photo or screen fits with almost no error;
// a real head does not. Direction: always the EARLIER frame to the LATER frame. Residual is divided by the inter-ocular
// distance, so it does not depend on how big the face is in the frame.
// Limits: a bent print is not a plane (known weakness R1-5, V1/V2/V4 still vote). The landmark model gives no per-point
// confidence, so only face-level quality (the tracker) gates the input. All thresholds are constructor parameters (TARGET).
package io.sajeev.passive.flatness;

import io.sajeev.api.Vote;
import io.sajeev.face.FaceFeatures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** V3: ATTACK when the landmarks of two views of the face are related by a single plane-to-plane mapping. */
public final class FlatnessVoter {

    /** About 34 MediaPipe ids spread over the oval, brows, eyes, nose, cheeks and mouth (some deep, some at the rim). */
    static final int[] POINTS = {
        10, 152, 234, 454, 127, 356, 70, 300, 105, 334, 33, 133, 362, 263, 159, 386,
        1, 4, 5, 6, 195, 197, 50, 280, 205, 425, 61, 291, 0, 17, 13, 172, 397, 58
    };
    private static final int LEFT_OUTER_EYE = 263;
    private static final int RIGHT_OUTER_EYE = 33;

    private final double minYawChange;
    private final double flatResidual;
    private final int iterations;
    private final double inlierThreshold;
    private final long seed;

    /**
     * @param minYawChange smallest yaw-proxy change between the two frames; below it there is no evidence (TARGET 0.14, about 8 degrees)
     * @param flatResidual RMS residual, in inter-ocular distances, below which the face moved like a plane (TARGET 0.02)
     * @param iterations RANSAC rounds (200)
     * @param inlierThreshold RANSAC inlier distance in inter-ocular distances (TARGET 0.03)
     * @param seed fixed seed so tests repeat exactly
     */
    public FlatnessVoter(double minYawChange, double flatResidual, int iterations, double inlierThreshold, long seed) {
        this.minYawChange = minYawChange;
        this.flatResidual = flatResidual;
        this.iterations = iterations;
        this.inlierThreshold = inlierThreshold;
        this.seed = seed;
    }

    /**
     * @param series landmark arrays (478 x 3), oldest first
     * @return ATTACK if the best-turned pair moves like a plane; LIVE if not; ABSTAIN when the head did not turn enough
     */
    public Vote vote(List<float[][]> series) {
        if (series == null || series.size() < 2) {
            return new Vote(Vote.Label.ABSTAIN, 0.0, "not_enough_frames");
        }
        int bi = -1;
        int bj = -1;
        double best = -1;
        double[] yaw = new double[series.size()];
        for (int i = 0; i < yaw.length; i++) {
            yaw[i] = FaceFeatures.yaw(series.get(i));
        }
        for (int i = 0; i < yaw.length; i++) {
            for (int j = i + 1; j < yaw.length; j++) {
                double d = Math.abs(yaw[j] - yaw[i]);
                if (d > best) {
                    best = d;
                    bi = i;
                    bj = j;
                }
            }
        }
        if (best < minYawChange) {
            return new Vote(Vote.Label.ABSTAIN, 0.0, "no_motion");
        }
        double r = residual(series.get(bi), series.get(bj));
        if (Double.isNaN(r)) {
            return new Vote(Vote.Label.ABSTAIN, 0.0, "degenerate_geometry");
        }
        double p = Math.min(1.0, r / (2.0 * flatResidual)); // the flat threshold maps to 0.5
        if (r < flatResidual) {
            return new Vote(Vote.Label.ATTACK, Math.min(p, 0.49), "flat_motion");
        }
        return new Vote(Vote.Label.LIVE, Math.max(p, 0.5), "three_d_motion");
    }

    /**
     * @param a landmarks of the earlier frame
     * @param b landmarks of the later frame
     * @return RMS error of the best plane-to-plane fit from a to b, in inter-ocular distances; NaN if it cannot be fitted
     */
    public double residual(float[][] a, float[][] b) {
        int n = POINTS.length;
        double[][] src = new double[n][2];
        double[][] dst = new double[n][2];
        for (int i = 0; i < n; i++) {
            src[i][0] = a[POINTS[i]][0];
            src[i][1] = a[POINTS[i]][1];
            dst[i][0] = b[POINTS[i]][0];
            dst[i][1] = b[POINTS[i]][1];
        }
        double iod = (dist(a[LEFT_OUTER_EYE], a[RIGHT_OUTER_EYE]) + dist(b[LEFT_OUTER_EYE], b[RIGHT_OUTER_EYE])) / 2.0;
        if (iod <= 1e-6) {
            return Double.NaN;
        }
        Random rnd = new Random(seed);
        int bestCount = -1;
        List<Integer> bestInliers = null;
        for (int it = 0; it < iterations; it++) {
            int[] idx = sample4(rnd, n);
            double[] h = fit(src, dst, idx);
            if (h == null) {
                continue;
            }
            List<Integer> in = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (error(h, src[i], dst[i]) / iod < inlierThreshold) {
                    in.add(i);
                }
            }
            if (in.size() > bestCount) {
                bestCount = in.size();
                bestInliers = in;
            }
        }
        if (bestInliers == null || bestInliers.size() < 4) {
            return Double.NaN;
        }
        int[] all = new int[bestInliers.size()];
        for (int i = 0; i < all.length; i++) {
            all[i] = bestInliers.get(i);
        }
        double[] h = fit(src, dst, all);
        if (h == null) {
            return Double.NaN;
        }
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double e = error(h, src[i], dst[i]) / iod;
            sum += e * e;
        }
        return Math.sqrt(sum / n);
    }

    private static double dist(float[] p, float[] q) {
        return Math.hypot(p[0] - q[0], p[1] - q[1]);
    }

    private static int[] sample4(Random rnd, int n) {
        int[] s = new int[4];
        for (int k = 0; k < 4; k++) {
            boolean ok;
            do {
                s[k] = rnd.nextInt(n);
                ok = true;
                for (int m = 0; m < k; m++) {
                    if (s[m] == s[k]) {
                        ok = false;
                    }
                }
            } while (!ok);
        }
        return s;
    }

    /** Error of mapping p through homography h, as a distance to q. Infinite when the point goes to infinity. */
    static double error(double[] h, double[] p, double[] q) {
        double w = h[6] * p[0] + h[7] * p[1] + 1.0;
        if (Math.abs(w) < 1e-12) {
            return Double.MAX_VALUE;
        }
        double x = (h[0] * p[0] + h[1] * p[1] + h[2]) / w;
        double y = (h[3] * p[0] + h[4] * p[1] + h[5]) / w;
        return Math.hypot(x - q[0], y - q[1]);
    }

    /** Least-squares homography (h33 = 1) from the chosen point pairs, in normalised coordinates; null if singular. */
    static double[] fit(double[][] src, double[][] dst, int[] idx) {
        double[] ts = normalisation(src, idx);
        double[] td = normalisation(dst, idx);
        double[][] ata = new double[8][8];
        double[] atb = new double[8];
        for (int id : idx) {
            double x = (src[id][0] - ts[0]) * ts[2];
            double y = (src[id][1] - ts[1]) * ts[2];
            double u = (dst[id][0] - td[0]) * td[2];
            double v = (dst[id][1] - td[1]) * td[2];
            double[] r1 = {x, y, 1, 0, 0, 0, -u * x, -u * y};
            double[] r2 = {0, 0, 0, x, y, 1, -v * x, -v * y};
            accumulate(ata, atb, r1, u);
            accumulate(ata, atb, r2, v);
        }
        double[] h = solve(ata, atb);
        if (h == null) {
            return null;
        }
        // H_pixels = Td^-1 * H_norm * Ts, where T maps p to (p - c) * s
        double[][] hn = {{h[0], h[1], h[2]}, {h[3], h[4], h[5]}, {h[6], h[7], 1.0}};
        double[][] tsM = {{ts[2], 0, -ts[0] * ts[2]}, {0, ts[2], -ts[1] * ts[2]}, {0, 0, 1}};
        double[][] tdInv = {{1 / td[2], 0, td[0]}, {0, 1 / td[2], td[1]}, {0, 0, 1}};
        double[][] m = mul(tdInv, mul(hn, tsM));
        double s = m[2][2];
        if (Math.abs(s) < 1e-12) {
            return null;
        }
        return new double[] {m[0][0] / s, m[0][1] / s, m[0][2] / s, m[1][0] / s, m[1][1] / s, m[1][2] / s,
            m[2][0] / s, m[2][1] / s};
    }

    private static double[] normalisation(double[][] pts, int[] idx) {
        double cx = 0;
        double cy = 0;
        for (int i : idx) {
            cx += pts[i][0];
            cy += pts[i][1];
        }
        cx /= idx.length;
        cy /= idx.length;
        double d = 0;
        for (int i : idx) {
            d += Math.hypot(pts[i][0] - cx, pts[i][1] - cy);
        }
        d /= idx.length;
        return new double[] {cx, cy, d < 1e-9 ? 1.0 : Math.sqrt(2.0) / d};
    }

    private static void accumulate(double[][] ata, double[] atb, double[] row, double rhs) {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                ata[i][j] += row[i] * row[j];
            }
            atb[i] += row[i] * rhs;
        }
    }

    private static double[][] mul(double[][] a, double[][] b) {
        double[][] c = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) {
                    c[i][j] += a[i][k] * b[k][j];
                }
            }
        }
        return c;
    }

    /** Gaussian elimination with partial pivoting; null when (nearly) singular. */
    private static double[] solve(double[][] a0, double[] b0) {
        int n = b0.length;
        double[][] a = new double[n][];
        for (int i = 0; i < n; i++) {
            a[i] = Arrays.copyOf(a0[i], n);
        }
        double[] b = Arrays.copyOf(b0, n);
        for (int c = 0; c < n; c++) {
            int p = c;
            for (int r = c + 1; r < n; r++) {
                if (Math.abs(a[r][c]) > Math.abs(a[p][c])) {
                    p = r;
                }
            }
            if (Math.abs(a[p][c]) < 1e-10) {
                return null;
            }
            double[] t = a[c];
            a[c] = a[p];
            a[p] = t;
            double tb = b[c];
            b[c] = b[p];
            b[p] = tb;
            for (int r = c + 1; r < n; r++) {
                double f = a[r][c] / a[c][c];
                for (int k = c; k < n; k++) {
                    a[r][k] -= f * a[c][k];
                }
                b[r] -= f * b[c];
            }
        }
        double[] x = new double[n];
        for (int r = n - 1; r >= 0; r--) {
            double s = b[r];
            for (int k = r + 1; k < n; k++) {
                s -= a[r][k] * x[k];
            }
            x[r] = s / a[r][r];
        }
        return x;
    }
}
