// #34 (WS1) | Bible §6 | Engine tuning that is not a PDF-level config key yet. All values TARGET until DEV tuning (#62). Java 11 only.
package io.sajeev.engine;

/** Plain values the engine needs besides the validated Config. */
public final class EngineSettings {
    /** Usable face frames collected before the passive voters run. */
    public int passiveFrames = 8;
    /** Time (in frame arrival time) without a decision before the session gives up with a guidance error. */
    public long passiveTimeoutMillis = 10_000;
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
    /** Test hook only: artificial delay per processed frame, to prove a slow engine never blocks the producer. */
    public long testDelayMillis = 0;
    /** The prompt counts as shown when the active stage starts (bench and tests). A UI sets this to false and calls markPrompted(). */
    public boolean autoPrompt = true;

    /** @return the defaults */
    public static EngineSettings defaults() {
        return new EngineSettings();
    }
}
