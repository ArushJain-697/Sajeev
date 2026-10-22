// #16 (WS1) | Bible §6.2, PDF M9 | Face tracker states: none, several, poor quality, ok. Java 11 only.
// Several faces PAUSE the check: the tracker never picks one face (PDF M9).
package io.sajeev.face;

import io.sajeev.api.ErrorState;
import java.util.Optional;

/** Turns one frame's detections and quality into a tracker state. */
public final class FaceTracker {

    /** Limits for POOR_QUALITY. Constructor parameters so they can come from config (P-H). */
    public static final class Limits {
        private final double minFaceShare;
        private final double minBrightness;
        private final double minSharpness;

        /**
         * @param minFaceShare smallest face box as a share of the frame (too small = poor)
         * @param minBrightness darkest acceptable mean grey level of the face crop
         * @param minSharpness lowest acceptable Laplacian variance of the face crop (blur = poor)
         */
        public Limits(double minFaceShare, double minBrightness, double minSharpness) {
            this.minFaceShare = minFaceShare;
            this.minBrightness = minBrightness;
            this.minSharpness = minSharpness;
        }
    }

    /** The result for one frame. */
    public static final class State {
        private final Optional<ErrorState> error;
        private final String reason;
        private final Detection face;

        State(Optional<ErrorState> error, String reason, Detection face) {
            this.error = error;
            this.reason = reason;
            this.face = face;
        }

        /** @return the error state; empty when the frame is usable */
        public Optional<ErrorState> error() {
            return error;
        }

        /** @return a short reason for audit, e.g. "too_small", "dark", "blurry" */
        public String reason() {
            return reason;
        }

        /** @return the single face when one was found, else null */
        public Detection face() {
            return face;
        }
    }

    private final Limits limits;

    /** @param limits the poor-quality limits */
    public FaceTracker(Limits limits) {
        this.limits = limits;
    }

    /**
     * @param detections boxes from the detector, already above the score threshold
     * @param quality metrics measured on the single face (or the whole frame when there is none)
     * @return the tracker state
     */
    public State assess(java.util.List<Detection> detections, FaceQuality.Metrics quality) {
        if (detections.isEmpty()) {
            return new State(Optional.of(ErrorState.FACE_NOT_DETECTED), "no_face", null);
        }
        if (detections.size() > 1) {
            return new State(Optional.of(ErrorState.MULTIPLE_FACES), "multiple_faces", null);
        }
        Detection face = detections.get(0);
        if (quality.faceShare() < limits.minFaceShare) {
            return new State(Optional.of(ErrorState.POOR_QUALITY), "too_small", face);
        }
        if (quality.brightness() < limits.minBrightness) {
            return new State(Optional.of(ErrorState.POOR_QUALITY), "dark", face);
        }
        if (quality.sharpness() < limits.minSharpness) {
            return new State(Optional.of(ErrorState.POOR_QUALITY), "blurry", face);
        }
        return new State(Optional.empty(), "ok", face);
    }
}
