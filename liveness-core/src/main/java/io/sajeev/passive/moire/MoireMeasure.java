// #19 (WS1) | Bible §6.4 | Moire measure: share of spectral energy in isolated mid/high-frequency peaks. Java 11 only.
// Thresholds live in the voter, not here. Only a crop of at least MIN_SIDE px is measured (else ABSTAIN).
package io.sajeev.passive.moire;

import java.awt.image.BufferedImage;

/** Pure measurement on a grey crop. No I/O, no model. */
public final class MoireMeasure {

    /** Crops smaller than this (px, either side) are not measured. */
    public static final int MIN_SIDE = 96;
    /** Fixed analysis size so thresholds are comparable across crops. */
    static final int N = 128;
    /** Bins within this radius of the DC term are excluded. */
    static final int DC_RADIUS = 3;
    /** A bin is an isolated peak when it exceeds this multiple of its 8 neighbours' mean. */
    static final double PEAK_RATIO = 4.0;

    private MoireMeasure() {
    }

    /**
     * @param grey a grey crop (any size >= MIN_SIDE on each side)
     * @return share of non-DC spectral energy in isolated peaks, in [0, 1]
     */
    public static double peakEnergyShare(BufferedImage grey) {
        double[] re = new double[N * N];
        double[] im = new double[N * N];
        double[] win = new double[N];
        for (int i = 0; i < N; i++) {
            win[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (N - 1)); // Hann window, avoids edge leakage
        }
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                int sx = x * grey.getWidth() / N;
                int sy = y * grey.getHeight() / N;
                double v = (grey.getRaster().getSample(sx, sy, 0)) / 255.0;
                re[y * N + x] = v * win[x] * win[y];
            }
        }
        Fft2d.transform(re, im, N);
        double[] p = new double[N * N];
        double total = 0.0;
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                int dy = Math.min(y, N - y);
                int dx = Math.min(x, N - x);
                if (dx * dx + dy * dy <= DC_RADIUS * DC_RADIUS) {
                    continue; // DC and its immediate neighbours are not counted
                }
                double v = re[y * N + x] * re[y * N + x] + im[y * N + x] * im[y * N + x];
                p[y * N + x] = v;
                total += v;
            }
        }
        if (total <= 0.0) {
            return 0.0;
        }
        double peaks = 0.0;
        for (int y = 1; y < N - 1; y++) {
            for (int x = 1; x < N - 1; x++) {
                double v = p[y * N + x];
                if (v <= 0.0) {
                    continue;
                }
                // ring of bins 3..4 away (a Hann-windowed sinusoid leaks into the 8 nearest bins)
                double nb = 0.0;
                int cnt = 0;
                for (int dy = -4; dy <= 4; dy++) {
                    for (int dx = -4; dx <= 4; dx++) {
                        int yy = y + dy;
                        int xx = x + dx;
                        if (Math.max(Math.abs(dx), Math.abs(dy)) < 3 || yy < 0 || yy >= N || xx < 0 || xx >= N) {
                            continue;
                        }
                        nb += p[yy * N + xx];
                        cnt++;
                    }
                }
                if (cnt > 0 && v > PEAK_RATIO * (nb / cnt)) {
                    peaks += v;
                }
            }
        }
        return peaks / total;
    }
}
