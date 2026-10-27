// Red-team 2 fix (frozen stream) | Does the picture ever change? A real camera always shows sensor noise, so consecutive frames
// of a live stream are never pixel-identical. A repeated still frame (replayed as "video") has a mean absolute difference of 0.
// Java 11 only. Pure computation on decoded images; no I/O.
package io.sajeev.face;

import io.sajeev.image.PixelImage;
import java.util.ArrayDeque;
import java.util.Deque;

/** Tracks the mean absolute difference (grey levels, 64x64 box-downscaled) between consecutive usable frames. */
public final class StreamMotion {

    /** Side of the grey thumbnail that frames are compared on. */
    public static final int SIDE = 64;

    private final int keep;
    private final Deque<Double> diffs = new ArrayDeque<>();
    private int[] previous;

    /** @param keep how many of the latest consecutive differences are remembered (the passive window length minus one) */
    public StreamMotion(int keep) {
        this.keep = Math.max(1, keep);
    }

    /** @param img the next usable frame in order */
    public void observe(PixelImage img) {
        int[] now = thumbnail(img);
        if (previous != null) {
            long sum = 0;
            for (int i = 0; i < now.length; i++) {
                sum += Math.abs(now[i] - previous[i]);
            }
            diffs.addLast(sum / 1000.0 / now.length); // the thumbnail holds grey * 1000
            while (diffs.size() > keep) {
                diffs.removeFirst();
            }
        }
        previous = now;
    }

    /** @return how many consecutive differences are held */
    public int pairs() {
        return diffs.size();
    }

    /** @return the largest of the held differences, or -1 when there is none */
    public double maxDifference() {
        double m = -1;
        for (double d : diffs) {
            m = Math.max(m, d);
        }
        return m;
    }

    /**
     * @param threshold the mean absolute difference (0..255) at or below which two frames count as "the same picture"
     * @param minPairs fewer held differences than this are not enough to say anything
     * @return true when enough pairs are held and every one of them is at or below the threshold
     */
    public boolean frozen(double threshold, int minPairs) {
        return diffs.size() >= minPairs && maxDifference() <= threshold;
    }

    /** Grey thumbnail by box averaging (every source pixel counts once, so sensor noise is averaged, not sampled). */
    static int[] thumbnail(PixelImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] out = new int[SIDE * SIDE];
        for (int ty = 0; ty < SIDE; ty++) {
            int y0 = (int) ((long) ty * h / SIDE);
            int y1 = Math.max(y0 + 1, (int) ((long) (ty + 1) * h / SIDE));
            for (int tx = 0; tx < SIDE; tx++) {
                int x0 = (int) ((long) tx * w / SIDE);
                int x1 = Math.max(x0 + 1, (int) ((long) (tx + 1) * w / SIDE));
                long acc = 0;
                int n = 0;
                for (int y = y0; y < y1 && y < h; y++) {
                    for (int x = x0; x < x1 && x < w; x++) {
                        int p = img.getRGB(x, y);
                        acc += 299 * ((p >> 16) & 0xFF) + 587 * ((p >> 8) & 0xFF) + 114 * (p & 0xFF);
                        n++;
                    }
                }
                // keep three decimals of precision (grey * 1000), so a tiny noise level is not rounded away
                out[ty * SIDE + tx] = n == 0 ? 0 : (int) (acc / n);
            }
        }
        return out;
    }
}
