// #51 (WS3) | Adapter 2: SBI apps without the stream extension give one rCapture image. Tier LIMITED, never full liveness.
package io.sajeev.engine.android;

import ai.onnxruntime.OrtException;
import io.sajeev.api.Verdict;
import io.sajeev.api.Vote;
import io.sajeev.face.Detection;
import io.sajeev.face.YuNetDetector;
import io.sajeev.image.ImageCodec;
import io.sajeev.image.PixelImage;
import io.sajeev.passive.edge.FrameEdgeVoter;
import io.sajeev.passive.reflection.ReflectionVoter;
import io.sajeev.passive.texture.MiniFasNetModel;
import io.sajeev.passive.texture.TextureCnnVoter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single-image check: V1 texture, V4 frame edge and V5 reflection on one capture. No motion, so no flatness, no
 * challenge and no Binding against live frames. The result is always tier LIMITED and carries {@code passed} only if
 * no voter said ATTACK and V1 said LIVE. Show it as "limited check", never as verified liveness.
 */
public final class CaptureOnlyChecker {

    /** The outcome. {@code tier} is always LIMITED. */
    public static final class Result {
        public final Verdict.Tier tier = Verdict.Tier.LIMITED;
        public final boolean passed;
        public final String reason;
        public final Map<String, Vote> votes;

        Result(boolean passed, String reason, Map<String, Vote> votes) {
            this.passed = passed;
            this.reason = reason;
            this.votes = votes;
        }
    }

    private final YuNetDetector detector;
    private final TextureCnnVoter texture;
    private final FrameEdgeVoter edge = new FrameEdgeVoter(0.5, 0.80, 12);
    private final ReflectionVoter reflection = new ReflectionVoter(0.02, 0.6, 0.15, 0.95);

    public CaptureOnlyChecker(YuNetDetector detector, MiniFasNetModel v2, MiniFasNetModel v1se) {
        this.detector = detector;
        this.texture = new TextureCnnVoter(v2, v1se, detector, 0.80, 0.20);
    }

    /** @param captureImage a decodable image (JPEG or PNG) @return the limited result; never "live" */
    public Result check(byte[] captureImage) {
        Map<String, Vote> votes = new LinkedHashMap<>();
        PixelImage img = ImageCodec.decode(captureImage);
        if (img == null) {
            return new Result(false, "undecodable_capture", votes);
        }
        try {
            List<Detection> faces = detector.detect(img, 0.6, 0.3);
            if (faces.size() != 1) {
                return new Result(false, faces.isEmpty() ? "no_face" : "multiple_faces", votes);
            }
            io.sajeev.api.Frame f = new io.sajeev.api.Frame(captureImage, System.nanoTime(), 0);
            votes.put("V1_texture_minifasnet", texture.vote(Collections.singletonList(f)));
            votes.put("V4_frame_edge", edge.vote(img, faces.get(0)));
            votes.put("V5_reflection", reflection.vote(img, faces.get(0)));
        } catch (OrtException e) {
            return new Result(false, "engine_error", votes);
        }
        boolean attack = false;
        for (Vote v : votes.values()) {
            attack |= v.label() == Vote.Label.ATTACK;
        }
        boolean v1Live = votes.get("V1_texture_minifasnet").label() == Vote.Label.LIVE;
        return new Result(!attack && v1Live, attack ? "attack_vote" : v1Live ? "limited_pass" : "texture_not_confident", votes);
    }
}
