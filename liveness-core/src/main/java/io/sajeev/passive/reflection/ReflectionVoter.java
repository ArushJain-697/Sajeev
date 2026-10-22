// #22 (WS1) | Bible §6.4 V5 | Reflection / glare voter. Java 11 only.
// Glare = near-white, low-saturation pixels in the face box, compared with the same frame outside the face.
// NOT built here: the chroma statistics half of the spec (see BUILD_LOG #22). Thresholds are constructor parameters.
// No threshold depends on skin tone (fairness rule, Bible §6.4).
package io.sajeev.passive.reflection;

import io.sajeev.api.Vote;
import io.sajeev.face.Detection;
import java.awt.image.BufferedImage;

/** Votes ATTACK when the face box is much more glaring than the background of the same frame. */
public final class ReflectionVoter {

    /** Luma (0..255) at or above which a pixel counts as near-white. */
    static final int WHITE_LUMA = 245;
    /** Saturation (0..1) at or below which a pixel counts as colourless (glare, not a coloured object). */
    static final double MAX_SAT = 0.12;

    private final double glareLimit;
    private final double ratioLimit;
    private final double darkLimit;
    private final double brightLimit;

    /**
     * @param glareLimit face glare share at or above which the face is glaring (TARGET, tune on DEV)
     * @param ratioLimit face glare must also be this many times the background glare (TARGET)
     * @param darkLimit mean face luma below this = extreme low light, ABSTAIN
     * @param brightLimit mean face luma above this = extreme high light, ABSTAIN
     */
    public ReflectionVoter(double glareLimit, double ratioLimit, double darkLimit, double brightLimit) {
        this.glareLimit = glareLimit;
        this.ratioLimit = ratioLimit;
        this.darkLimit = darkLimit;
        this.brightLimit = brightLimit;
    }

    /**
     * @param frame the full frame (RGB)
     * @param face the detected face box in frame pixels
     * @return LIVE-side or ATTACK-side vote, or ABSTAIN under extreme lighting
     */
    public Vote vote(BufferedImage frame, Detection face) {
        int[] fx = bounds(face, frame.getWidth(), frame.getHeight());
        double faceLuma = 0;
        int faceN = 0;
        int faceGlare = 0;
        int bgN = 0;
        int bgGlare = 0;
        for (int y = 0; y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                int rgb = frame.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int luma = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
                boolean glare = luma >= WHITE_LUMA && saturation(r, g, b) <= MAX_SAT;
                boolean inFace = x >= fx[0] && x < fx[2] && y >= fx[1] && y < fx[3];
                if (inFace) {
                    faceN++;
                    faceLuma += luma;
                    if (glare) {
                        faceGlare++;
                    }
                } else {
                    bgN++;
                    if (glare) {
                        bgGlare++;
                    }
                }
            }
        }
        if (faceN == 0) {
            return new Vote(Vote.Label.ABSTAIN, 0.0, "empty_face_box");
        }
        double meanLuma = faceLuma / faceN;
        if (meanLuma < darkLimit || meanLuma > brightLimit) {
            return new Vote(Vote.Label.ABSTAIN, 0.0, "extreme_lighting luma=" + Math.round(meanLuma));
        }
        double faceShare = faceGlare / (double) faceN;
        double bgShare = bgN == 0 ? 0 : bgGlare / (double) bgN;
        boolean attack = faceShare >= glareLimit && faceShare >= ratioLimit * Math.max(bgShare, 1e-6);
        double live = attack ? 0.1 : 0.9;
        return new Vote(attack ? Vote.Label.ATTACK : Vote.Label.LIVE, live,
                "face_glare=" + String.format("%.3f", faceShare) + " bg_glare=" + String.format("%.3f", bgShare));
    }

    private static double saturation(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        return max == 0 ? 0 : (max - min) / (double) max;
    }

    private static int[] bounds(Detection d, int w, int h) {
        int x0 = Math.max(0, (int) Math.round(d.x()));
        int y0 = Math.max(0, (int) Math.round(d.y()));
        int x1 = Math.min(w, (int) Math.round(d.x() + d.w()));
        int y1 = Math.min(h, (int) Math.round(d.y() + d.h()));
        return new int[] {x0, y0, x1, y1};
    }
}
