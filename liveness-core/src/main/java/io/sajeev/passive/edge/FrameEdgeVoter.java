// #21 (WS1) | Bible §6.4 V4 | Frame-edge voter: a straight rectangle that encloses the face box = a screen or print edge. Java 11 only.
// Method: Sobel edges, then horizontal and vertical line votes (a coarse Hough accumulator on row and column sums).
// A rectangle counts only if it has a line on all four sides, enclosing the face, and the face is not the whole frame.
// Limits: axis-aligned edges only (a tilted phone is not caught yet). Thresholds are constructor parameters (TARGET).
package io.sajeev.passive.edge;

import io.sajeev.api.Vote;
import io.sajeev.face.Detection;
import io.sajeev.image.PixelImage;

/** V4: finds axis-aligned straight edges that frame the face. */
public final class FrameEdgeVoter {

    private final double minEdgeShare;
    private final double maxFaceShare;
    private final double margin;

    /**
     * @param minEdgeShare a row or column counts as a line when this share of its pixels are strong edges (TARGET 0.5)
     * @param maxFaceShare ABSTAIN when the face fills more than this share of the frame (PDF/Bible: 0.80)
     * @param margin how far outside the face box a side line may sit, in pixels (TARGET, scales with the face)
     */
    public FrameEdgeVoter(double minEdgeShare, double maxFaceShare, double margin) {
        this.minEdgeShare = minEdgeShare;
        this.maxFaceShare = maxFaceShare;
        this.margin = margin;
    }

    /**
     * @param frame the frame
     * @param face the face box
     * @return ATTACK if all four sides of a frame edge enclose the face, LIVE-side otherwise, ABSTAIN when the face fills the frame
     */
    public Vote vote(PixelImage frame, Detection face) {
        int w = frame.getWidth();
        int h = frame.getHeight();
        double faceShare = (face.w() * face.h()) / ((double) w * h);
        if (faceShare > maxFaceShare) {
            return new Vote(Vote.Label.ABSTAIN, 0.0, "face_fills_frame");
        }
        boolean[] strong = strongEdges(frame);
        int[] rowCount = new int[h];
        int[] colCount = new int[w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (strong[y * w + x]) {
                    rowCount[y]++;
                    colCount[x]++;
                }
            }
        }
        int fx0 = (int) Math.round(face.x());
        int fy0 = (int) Math.round(face.y());
        int fx1 = (int) Math.round(face.x() + face.w());
        int fy1 = (int) Math.round(face.y() + face.h());
        // A side may sit anywhere from the margin outside the face up to 1.5 face-sizes away (a bezel is rarely tight)
        int reachY = (int) Math.round(1.5 * face.h());
        int reachX = (int) Math.round(1.5 * face.w());
        int m = (int) Math.round(margin);
        boolean top = hasLine(rowCount, Math.max(0, fy0 - reachY), fy0 - m, fx0, fx1, true, w, strong);
        boolean bottom = hasLine(rowCount, fy1 + m, Math.min(h - 1, fy1 + reachY), fx0, fx1, true, w, strong);
        boolean left = hasLine(colCount, Math.max(0, fx0 - reachX), fx0 - m, fy0, fy1, false, w, strong);
        boolean right = hasLine(colCount, fx1 + m, Math.min(w - 1, fx1 + reachX), fy0, fy1, false, w, strong);
        boolean enclosed = top && bottom && left && right;
        return new Vote(enclosed ? Vote.Label.ATTACK : Vote.Label.LIVE, enclosed ? 0.1 : 0.9,
                "frame_edges top=" + top + " bottom=" + bottom + " left=" + left + " right=" + right);
    }

    /** A side is a line if some row (or column) in [lo, hi] has at least minEdgeShare strong edges over the face span. */
    private boolean hasLine(int[] counts, int from, int to, int spanA, int spanB, boolean horizontal,
                            int w, boolean[] strong) {
        int lo = Math.max(0, Math.min(from, to));
        int hi = Math.min(counts.length - 1, Math.max(from, to));
        int span = Math.max(1, spanB - spanA);
        for (int r = lo; r <= hi; r++) {
            int hits = 0;
            for (int k = spanA; k < spanB; k++) {
                int idx = horizontal ? r * w + k : k * w + r;
                if (idx >= 0 && idx < strong.length && strong[idx]) {
                    hits++;
                }
            }
            if (hits / (double) span >= minEdgeShare) {
                return true;
            }
        }
        return false;
    }

    /** Sobel magnitude on luma, thresholded at 40% of the frame's maximum. */
    static boolean[] strongEdges(PixelImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        double[] mag = new double[w * h];
        double max = 0;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                double gx = -luma(img, x - 1, y - 1) - 2 * luma(img, x - 1, y) - luma(img, x - 1, y + 1)
                        + luma(img, x + 1, y - 1) + 2 * luma(img, x + 1, y) + luma(img, x + 1, y + 1);
                double gy = -luma(img, x - 1, y - 1) - 2 * luma(img, x, y - 1) - luma(img, x + 1, y - 1)
                        + luma(img, x - 1, y + 1) + 2 * luma(img, x, y + 1) + luma(img, x + 1, y + 1);
                double m = Math.sqrt(gx * gx + gy * gy);
                mag[y * w + x] = m;
                max = Math.max(max, m);
            }
        }
        boolean[] out = new boolean[w * h];
        double cut = 0.4 * max;
        for (int i = 0; i < mag.length; i++) {
            out[i] = mag[i] >= cut && cut > 0;
        }
        return out;
    }

    private static double luma(PixelImage img, int x, int y) {
        int rgb = img.getRGB(x, y);
        return 0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF);
    }
}
