// #34 (WS1) | Bible §6 | Engine tuning that is not a PDF-level config key yet. All values TARGET until DEV tuning (#62). Java 11 only.
package io.sajeev.engine;

/** Plain values the engine needs besides the validated Config. */
public final class EngineSettings {
    /** Usable face frames collected before the passive voters run. */
    public int passiveFrames = 8;
    /** Time (in frame arrival time) without a decision before the session gives up with a guidance error. */
    public long passiveTimeoutMillis = 10_000;
    /** Reflection voter (V5): face glare share, glare ratio to the background, and the mean-luma range (0..255) it can judge. TARGET. */
    public double reflectionGlare = 0.02;
    public double reflectionRatio = 4.0;
    public double reflectionDark = 40;
    public double reflectionBright = 215;
    /** Wall-clock time without any delivered frame (after the first one) before the session ends as a lost device. */
    public long streamStallMillis = 5_000;
    /** The bounded frame queue; the oldest frame is dropped when it is full. */
    public int queueCapacity = 8;
    /** FaceTracker limits. */
    public double minFaceShare = 0.02;
    public double minBrightness = 40.0;
    public double minSharpness = 50.0;
    /** One in this many usable frames is embedded for Binding. */
    public int bindingEveryNth = 3;
    /** Binding geometry tolerance (the config value is "calibrated-on-DEV" until #62). */
    public double geometryTolerance = 0.08;
    /** Voter thresholds. */
    public double textureLive = 0.80;
    public double textureAttack = 0.20;
    public double moireShare = 0.20;
    public double edgeMinShare = 0.5;
    public double edgeMaxFaceShare = 0.80;
    public double edgeMargin = 12;
    public double flatMinYaw = 0.14;
    public double flatResidual = 0.02;
    /** Frozen stream: mean absolute grey difference (0..255, 64x64 thumbnails) at or below which consecutive frames are "the same picture". TARGET 0.02: identical frames give 0.000; the 9 real DEV clips (one person, one webcam, very still) never had 7 consecutive pairs below 0.055. */
    public double frozenMaxMeanAbsDiff = 0.02;
    /** Frozen stream: how many consecutive-frame differences must all be under the limit before the stream is called frozen. */
    public int frozenMinPairs = 5;
    /** Least time (on the frame arrival clock) of usable frames that must be observed before a passive pass. TARGET. */
    public long minPassiveMillis = 700;
    /** A gap between two accepted frames longer than this is clamped to it (an absurd timestamp jump). */
    public long maxArrivalGapMillis = 10_000;
    /** Frames with an unusable arrival time (not strictly increasing, out of range) tolerated before the session ends as INVALID_FRAME_DATA. */
    public int maxRejectedTimestamps = 10;
    /** Largest width or height of a frame or capture image we agree to decode. */
    public int maxImageSide = 8192;
    /** Largest width x height of a frame or capture image we agree to decode (40 megapixels). */
    public long maxImagePixels = 40_000_000L;
    /** Sessions that may run at the same time in one engine; the next start ends at once with ENGINE_ERROR. */
    public int maxConcurrentSessions = 16;
    /**
     * Evaluation mode: no load shedding, no wall-clock stall timer and a fixed challenge seed, so the same frames always give the
     * same verdict. Used by run_eval and the bench together with {@code EngineSession.awaitIdle(long)}.
     */
    public boolean deterministic = false;
    /** Test hook only: artificial delay per processed frame, to prove a slow engine never blocks the producer. */
    public long testDelayMillis = 0;
    /** The prompt counts as shown when the active stage starts (bench and tests). A UI sets this to false and calls markPrompted(). */
    public boolean autoPrompt = true;

    /** @return the defaults */
    public static EngineSettings defaults() {
        return new EngineSettings();
    }
}
