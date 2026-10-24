// #27 (WS1) | Bible §6.7 | Binding: is the face that gets stored the face that was seen live? Java 11 only.
// Four checks: exactly one face, embedding continuity, landmark-geometry continuity, and timing.
// All limits are parameters (TARGET, tuned on DEV so each signal rejects at most 1% of honest presentations, #62).
package io.sajeev.binding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Keeps the best live crops and checks the stored capture against them. */
public final class Binding {

    /** BOUND means the capture matches the live face. CAPTURE_UNBOUND is a failed attempt (generic PAD-style message). */
    public enum Verdict { BOUND, CAPTURE_UNBOUND }

    /** The verdict and which checks failed, for audit. */
    public static final class Result {
        private final Verdict verdict;
        private final List<String> failedChecks;
        private final double bestCosine;
        private final double geometryDifference;

        Result(Verdict verdict, List<String> failedChecks, double bestCosine, double geometryDifference) {
            this.verdict = verdict;
            this.failedChecks = failedChecks;
            this.bestCosine = bestCosine;
            this.geometryDifference = geometryDifference;
        }

        public Verdict verdict() { return verdict; }
        /** @return names of the failed checks: faces, no_live_evidence, embedding, geometry, timing */
        public List<String> failedChecks() { return failedChecks; }
        public double bestCosine() { return bestCosine; }
        public double geometryDifference() { return geometryDifference; }
    }

    private static final class Observation {
        final float[] embedding;
        final double[] geometry;
        final double quality;

        Observation(float[] embedding, double[] geometry, double quality) {
            this.embedding = embedding;
            this.geometry = geometry;
            this.quality = quality;
        }
    }

    private final double embeddingThreshold;
    private final double geometryTolerance;
    private final long windowMarginMillis;
    private final int ringSize;
    private final boolean timingCheckEnabled;
    private final List<Observation> ring = new ArrayList<>();
    private volatile long liveVerdictNanos = -1; // written by the session's worker, read by the caller's thread

    /**
     * @param embeddingThreshold cosine at or above which the embeddings match (0.363 is OpenCV's verification default,
     *                           a starting point, not a calibration)
     * @param geometryTolerance largest relative difference in the geometry ratios (TARGET 0.08)
     * @param windowMarginMillis extra time allowed after the device's capture timeout (binding.windowMarginMs, 2000)
     * @param ringSize how many of the best live observations to keep
     */
    public Binding(double embeddingThreshold, double geometryTolerance, long windowMarginMillis, int ringSize) {
        this(embeddingThreshold, geometryTolerance, windowMarginMillis, ringSize, true);
    }

    /** Same, with the timing check exposed so the mutation harness (#31) can switch it off. Production passes true. */
    public Binding(double embeddingThreshold, double geometryTolerance, long windowMarginMillis, int ringSize,
                   boolean timingCheckEnabled) {
        this.timingCheckEnabled = timingCheckEnabled;
        this.embeddingThreshold = embeddingThreshold;
        this.geometryTolerance = geometryTolerance;
        this.windowMarginMillis = windowMarginMillis;
        this.ringSize = ringSize;
    }

    /**
     * Record one live observation during the session. Only the best ones are kept.
     * @param embedding the face embedding on this frame
     * @param geometry the landmark-geometry ratios on this frame
     * @param quality a quality score; higher is better
     */
    public void observeLive(float[] embedding, double[] geometry, double quality) {
        ring.add(new Observation(embedding, geometry, quality));
        ring.sort(Comparator.comparingDouble((Observation o) -> o.quality).reversed());
        while (ring.size() > ringSize) {
            ring.remove(ring.size() - 1);
        }
    }

    /** @param nanos the monotonic time of the liveness verdict */
    public void liveVerdictAt(long nanos) {
        this.liveVerdictNanos = nanos;
    }

    /**
     * Check the stored capture.
     * @param facesInCapture how many faces the capture contains
     * @param captureEmbedding the capture's embedding
     * @param captureGeometry the capture's geometry ratios
     * @param captureArrivalNanos monotonic time when the capture came back
     * @param captureTimeoutMillis the device's capture timeout (the stream stops while a capture runs)
     * @return BOUND only when every check passes
     */
    public Result bind(int facesInCapture, float[] captureEmbedding, double[] captureGeometry,
                       long captureArrivalNanos, long captureTimeoutMillis) {
        List<String> failed = new ArrayList<>();
        if (facesInCapture != 1) {
            failed.add("faces");
        }
        double best = -1.0;
        double geomDiff = Double.MAX_VALUE;
        if (ring.isEmpty()) {
            failed.add("no_live_evidence");
        } else {
            for (Observation o : ring) {
                best = Math.max(best, SfaceEmbedder.cosine(o.embedding, captureEmbedding));
                geomDiff = Math.min(geomDiff, GeometryRatios.maxRelativeDifference(captureGeometry, o.geometry));
            }
            if (!(best >= embeddingThreshold)) { // NaN (a zero vector) fails too
                failed.add("embedding");
            }
            if (!(geomDiff <= geometryTolerance)) {
                failed.add("geometry");
            }
        }
        long allowedNanos = (captureTimeoutMillis + windowMarginMillis) * 1_000_000L;
        if (timingCheckEnabled && (liveVerdictNanos < 0 || captureArrivalNanos - liveVerdictNanos > allowedNanos)) {
            failed.add("timing");
        }
        return new Result(failed.isEmpty() ? Verdict.BOUND : Verdict.CAPTURE_UNBOUND, failed, best, geomDiff);
    }
}
