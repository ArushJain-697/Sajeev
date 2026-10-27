// #27 (WS1) | Bible §6.7 | Binding: is the face that gets stored the face that was seen live? Java 11 only.
// Five checks: exactly one face, one person throughout the session, embedding continuity, landmark-geometry continuity
// (both against the SAME live observation), and timing.
// All limits are parameters (TARGET, tuned on DEV so each signal rejects at most 1% of honest presentations, #62).
package io.sajeev.binding;

import java.util.ArrayList;
import java.util.Collections;
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

        /** @param check the one failed check (for example "no_live_verdict" or "already_used") @return an unbound result */
        public static Result unbound(String check) {
            return new Result(Verdict.CAPTURE_UNBOUND, Collections.singletonList(check), -1.0, Double.MAX_VALUE);
        }

        public Verdict verdict() { return verdict; }
        /** @return names of the failed checks: faces, identity_changed, no_live_evidence, embedding, geometry, timing */
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
    private float[] anchor; // the first accepted live embedding: everyone seen later must be this person
    private int disagreements;
    private volatile boolean identityChanged;
    private volatile boolean hasVerdict; // written by the session's worker (or abort), read by the caller's thread
    private volatile long liveVerdictNanos;

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
     * Record one live observation during the session. Only the best ones are kept. The first one becomes the anchor: a later
     * observation that does not match it is not kept, and two or more of them mean the person changed during the session
     * (the capture then cannot be bound).
     * @param embedding the face embedding on this frame
     * @param geometry the landmark-geometry ratios on this frame
     * @param quality a quality score; higher is better
     */
    public synchronized void observeLive(float[] embedding, double[] geometry, double quality) {
        if (anchor == null) {
            anchor = embedding;
        } else if (!(SfaceEmbedder.cosine(anchor, embedding) >= embeddingThreshold)) { // NaN fails too
            disagreements++;
            if (disagreements >= 2) {
                identityChanged = true;
            }
            return;
        }
        ring.add(new Observation(embedding, geometry, quality));
        ring.sort(Comparator.comparingDouble((Observation o) -> o.quality).reversed());
        while (ring.size() > ringSize) {
            ring.remove(ring.size() - 1);
        }
    }

    /** @return true when two or more live frames did not match the first one (a different person appeared during the session) */
    public boolean identityChanged() {
        return identityChanged;
    }

    /** @param nanos the monotonic time of the liveness verdict */
    public void liveVerdictAt(long nanos) {
        this.liveVerdictNanos = nanos;
        this.hasVerdict = true;
    }

    /** Forget the verdict time (the session was aborted, so nothing can be bound to it). */
    public void clearVerdict() {
        this.hasVerdict = false;
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
    public synchronized Result bind(int facesInCapture, float[] captureEmbedding, double[] captureGeometry,
                       long captureArrivalNanos, long captureTimeoutMillis) {
        List<String> failed = new ArrayList<>();
        if (facesInCapture != 1) {
            failed.add("faces");
        }
        if (identityChanged) {
            failed.add("identity_changed");
        }
        double best = -1.0;
        double geomDiff = Double.MAX_VALUE;
        if (ring.isEmpty()) {
            failed.add("no_live_evidence");
        } else {
            boolean anyBoth = false;
            boolean anyCosine = false;
            boolean anyGeometry = false;
            for (Observation o : ring) {
                double c = SfaceEmbedder.cosine(o.embedding, captureEmbedding);
                double g = GeometryRatios.maxRelativeDifference(captureGeometry, o.geometry);
                best = Math.max(best, c);
                geomDiff = Math.min(geomDiff, g);
                boolean cosOk = c >= embeddingThreshold; // NaN (a zero vector) fails
                boolean geomOk = g <= geometryTolerance;
                anyCosine |= cosOk;
                anyGeometry |= geomOk;
                anyBoth |= cosOk && geomOk;
            }
            if (!anyBoth) {
                // both signals must agree on the SAME live observation: matching two different ones is not a match
                if (!anyCosine) {
                    failed.add("embedding");
                }
                if (!anyGeometry || anyCosine) {
                    failed.add("geometry");
                }
            }
        }
        if (timingCheckEnabled && !timingOk(captureArrivalNanos, captureTimeoutMillis)) {
            failed.add("timing");
        }
        return new Result(failed.isEmpty() ? Verdict.BOUND : Verdict.CAPTURE_UNBOUND, failed, best, geomDiff);
    }

    private boolean timingOk(long captureArrivalNanos, long captureTimeoutMillis) {
        if (!hasVerdict) {
            return false;
        }
        if (captureTimeoutMillis < 0 || windowMarginMillis < 0) {
            return false;
        }
        long millis = captureTimeoutMillis + windowMarginMillis;
        if (millis < 0) { // the sum overflowed: saturate
            millis = Long.MAX_VALUE;
        }
        long allowedNanos = millis > Long.MAX_VALUE / 1_000_000L ? Long.MAX_VALUE : millis * 1_000_000L;
        long diff;
        try {
            diff = Math.subtractExact(captureArrivalNanos, liveVerdictNanos);
        } catch (ArithmeticException e) {
            return false;
        }
        return diff <= allowedNanos;
    }
}
