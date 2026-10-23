// #18 (WS1) | Bible §6.3 | Voter V1: texture CNN, two MiniFASNet models averaged. Java 11 only.
// Thresholds are constructor parameters (TARGET, tune on DEV only; move into config in #28).
package io.sajeev.passive.texture;

import ai.onnxruntime.OrtException;
import io.sajeev.api.Frame;
import io.sajeev.api.PadVoter;
import io.sajeev.api.Vote;
import io.sajeev.face.Detection;
import io.sajeev.face.YuNetDetector;
import io.sajeev.image.PixelImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import io.sajeev.image.ImageCodec;

/**
 * V1: averages the live probability of MiniFASNetV2 (scale 2.7) and MiniFASNetV1SE (scale 4.0).
 * Averages, never the max (Bible §6.3). ABSTAIN between the two thresholds.
 */
public final class TextureCnnVoter implements PadVoter {

    private final MiniFasNetModel v2;
    private final MiniFasNetModel v1se;
    private final YuNetDetector detector;
    private final io.sajeev.face.FrameAnalysis analysis; // null: decode and detect here
    private final double liveThreshold;
    private final double attackThreshold;

    /**
     * @param v2 MiniFASNetV2 (scale 2.7)
     * @param v1se MiniFASNetV1SE (scale 4.0)
     * @param detector the face detector, used to find the face in the last frame of the window
     * @param liveThreshold vote LIVE at or above this probability (0.80 TARGET)
     * @param attackThreshold vote ATTACK at or below this probability (0.20 TARGET)
     */
    public TextureCnnVoter(MiniFasNetModel v2, MiniFasNetModel v1se, YuNetDetector detector,
                           double liveThreshold, double attackThreshold) {
        this(v2, v1se, detector, liveThreshold, attackThreshold, null);
    }

    /** Same, sharing decoding and detection with the other voters through {@code analysis}. */
    public TextureCnnVoter(MiniFasNetModel v2, MiniFasNetModel v1se, YuNetDetector detector,
                           double liveThreshold, double attackThreshold, io.sajeev.face.FrameAnalysis analysis) {
        this.analysis = analysis;
        this.v2 = v2;
        this.v1se = v1se;
        this.detector = detector;
        this.liveThreshold = liveThreshold;
        this.attackThreshold = attackThreshold;
    }

    @Override
    public String name() {
        return "V1_texture_minifasnet";
    }

    @Override
    public Vote vote(List<Frame> window) {
        if (window.isEmpty()) {
            return new Vote(Vote.Label.ABSTAIN, 0.0, "empty_window");
        }
        try {
            PixelImage img = analysis != null ? analysis.image(window.get(window.size() - 1)) : ImageCodec.decode(window.get(window.size() - 1).jpeg());
            if (img == null) {
                return new Vote(Vote.Label.ABSTAIN, 0.0, "undecodable_frame");
            }
            List<Detection> faces = analysis != null ? analysis.faces(window.get(window.size() - 1)) : detector.detect(img, 0.6, 0.3);
            if (faces.size() != 1) {
                return new Vote(Vote.Label.ABSTAIN, 0.0, "not_one_face");
            }
            double p = (v2.liveProbability(img, faces.get(0)) + v1se.liveProbability(img, faces.get(0))) / 2.0;
            return new Vote(label(p), p, "texture_prob=" + String.format("%.3f", p));
        } catch (OrtException e) {
            return new Vote(Vote.Label.ABSTAIN, 0.0, "engine_error");
        }
    }

    /** @return the label for a live probability, between the two thresholds it is ABSTAIN */
    Vote.Label label(double p) {
        if (p >= liveThreshold) {
            return Vote.Label.LIVE;
        }
        if (p <= attackThreshold) {
            return Vote.Label.ATTACK;
        }
        return Vote.Label.ABSTAIN;
    }
}
