// #19 (WS1) | Bible §6.4 V2 | Moiré voter: isolated mid/high-frequency spectral peaks on the face crop. Java 11 only.
// ATTACK when the share is over the limit in at least 2 of the last 3 frames; ABSTAIN when the face crop is under 96 px.
// Limits are TARGET values (tune on DEV, #62). Validated only on one clean photo and a synthetic grating (see BUILD_LOG #19).
package io.sajeev.passive.moire;

import io.sajeev.api.Frame;
import io.sajeev.api.PadVoter;
import io.sajeev.api.Vote;
import io.sajeev.face.Detection;
import io.sajeev.face.YuNetDetector;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

/** V2: votes ATTACK on repeated moiré across recent frames. */
public final class MoireVoter implements PadVoter {

    private final YuNetDetector detector;
    private final double shareLimit;

    /**
     * @param detector face detector (finds the face in each frame)
     * @param shareLimit peak-energy share at or above which a frame is flagged (TARGET 0.20)
     */
    public MoireVoter(YuNetDetector detector, double shareLimit) {
        this.detector = detector;
        this.shareLimit = shareLimit;
    }

    @Override
    public String name() {
        return "V2_moire";
    }

    @Override
    public Vote vote(List<Frame> window) {
        int from = Math.max(0, window.size() - 3);
        int flagged = 0;
        int counted = 0;
        for (Frame f : window.subList(from, window.size())) {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(f.jpeg()));
                if (img == null) {
                    continue;
                }
                List<Detection> faces = detector.detect(img, 0.6, 0.3);
                if (faces.size() != 1) {
                    continue;
                }
                Detection d = faces.get(0);
                if (Math.min(d.w(), d.h()) < MoireMeasure.MIN_SIDE) {
                    return new Vote(Vote.Label.ABSTAIN, 0.0, "crop_under_96px");
                }
                BufferedImage crop = grey(img, d);
                counted++;
                if (MoireMeasure.peakEnergyShare(crop) >= shareLimit) {
                    flagged++;
                }
            } catch (IOException | ai.onnxruntime.OrtException e) {
                return new Vote(Vote.Label.ABSTAIN, 0.0, "engine_error");
            }
        }
        if (counted < 2) {
            return new Vote(Vote.Label.ABSTAIN, 0.0, "fewer_than_two_usable_frames");
        }
        boolean attack = flagged >= 2;
        return new Vote(attack ? Vote.Label.ATTACK : Vote.Label.LIVE, attack ? 0.1 : 0.9,
                "moire_flagged=" + flagged + " of " + counted);
    }

    private static BufferedImage grey(BufferedImage img, Detection d) {
        int x = (int) Math.max(0, Math.round(d.x()));
        int y = (int) Math.max(0, Math.round(d.y()));
        int w = (int) Math.min(img.getWidth() - x, Math.round(d.w()));
        int h = (int) Math.min(img.getHeight() - y, Math.round(d.h()));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        out.createGraphics().drawImage(img.getSubimage(x, y, w, h), 0, 0, null);
        return out;
    }
}
