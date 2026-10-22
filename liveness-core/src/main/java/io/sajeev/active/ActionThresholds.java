// #25 (WS1) | Limits for the action detectors, as parameters (never literals in the logic). TARGET values; tune on DEV (#62).
package io.sajeev.active;

/** Ratios relative to the person's own neutral values. */
public final class ActionThresholds {
    /** Smile: mouth width ratio over neutral must exceed this (Bible: 1.25). */
    public final double smileRatio;
    /** Open mouth: aperture ratio rise over neutral must exceed this. */
    public final double mouthOpenRise;
    /** Turn head: yaw proxy change from neutral (0.20 TARGET). */
    public final double yawChange;
    /** Look left or right: gaze x change from neutral, in eye widths (0.07 on the synthetic fixtures). */
    public final double gazeHorizontal;
    /** Look up: gaze y change (negative direction) AND a pitch change. */
    public final double gazeUp;
    /** Look down. */
    public final double gazeDown;
    /** Look up or down also needs the head pitch to change by at least this (proxy units). */
    public final double pitchChange;
    /** EAR below this counts as closed (0.12). */
    public final double earClosed;
    /** Blink: EAR must dip below this fraction of the neutral EAR (0.6) and come back above 0.85 of it. */
    public final double blinkDip;
    /** Frames an action must be held (2), except a blink. */
    public final int heldFrames;
    /** The eyes must stay closed at least this long for EYES_CLOSED_HOLD, so a normal blink is not a hold (400 ms). */
    public final long closedHoldMillis;

    public ActionThresholds(double smileRatio, double mouthOpenRise, double yawChange, double gazeHorizontal,
                            double gazeUp, double gazeDown, double pitchChange, double earClosed, double blinkDip,
                            int heldFrames, long closedHoldMillis) {
        this.smileRatio = smileRatio;
        this.mouthOpenRise = mouthOpenRise;
        this.yawChange = yawChange;
        this.gazeHorizontal = gazeHorizontal;
        this.gazeUp = gazeUp;
        this.gazeDown = gazeDown;
        this.pitchChange = pitchChange;
        this.earClosed = earClosed;
        this.blinkDip = blinkDip;
        this.heldFrames = heldFrames;
        this.closedHoldMillis = closedHoldMillis;
    }

    /** @return the TARGET defaults */
    public static ActionThresholds defaults() {
        return new ActionThresholds(1.25, 0.10, 0.20, 0.07, 0.04, 0.02, 0.06, 0.12, 0.6, 2, 400L);
    }
}
