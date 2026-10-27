// Red team 2 (27 Oct 2026) | Voter V6: eyelid motion. Java 11 only.
// Evidence of life that a still picture cannot give: the eyelids of a real person move (blinks, tremor, saccade-driven lid motion)
// so the eye aspect ratio varies over a few seconds. A photograph shown on a phone only shakes with the hand: in the first real
// attack clip its eye aspect ratio varied by 0.034 over 8.6 s, against 0.07 to 0.8 for the real person in 3 s.
// This voter only ever says LIVE or ABSTAIN. Stillness proves nothing (a person staring at the camera can be still for 9 s), so a
// still face is not called an attack; it simply does not earn the live vote and the client falls back to the active challenge.
// A video replay of a real person blinks too, so this voter cannot stop a replay of a recording: only the challenge can.
package io.sajeev.passive.motion;

import io.sajeev.active.FrameFeatures;
import io.sajeev.api.Vote;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Votes LIVE when the eye aspect ratio moved enough over the observed time. */
public final class EyelidMotionVoter {

    /** Name used in votes and in the engine's positive-evidence list. */
    public static final String NAME = "V6_eyelid_motion";

    private final double minRange;
    private final long minSpanNanos;

    /**
     * @param minRange smallest spread of the eye aspect ratio (second largest minus second smallest value, so one bad landmark
     *                 frame cannot create it) that counts as eyelid motion (TARGET, tune on DEV)
     * @param minSpanNanos least time between the first and the last observed frame before the voter will judge
     */
    public EyelidMotionVoter(double minRange, long minSpanNanos) {
        this.minRange = minRange;
        this.minSpanNanos = minSpanNanos;
    }

    /**
     * @param features one entry per usable frame, oldest first
     * @return LIVE (0.9) with the measured range, or ABSTAIN
     */
    public Vote vote(List<FrameFeatures> features) {
        if (features.size() < 8) {
            return new Vote(Vote.Label.ABSTAIN, 0.0, "too_few_frames");
        }
        long span = features.get(features.size() - 1).arrivalNanos() - features.get(0).arrivalNanos();
        if (span < minSpanNanos) {
            return new Vote(Vote.Label.ABSTAIN, 0.0, "watched_too_short");
        }
        List<Double> ear = new ArrayList<>();
        for (FrameFeatures f : features) {
            double e = f.ear();
            if (!Double.isNaN(e) && !Double.isInfinite(e)) {
                ear.add(e);
            }
        }
        if (ear.size() < 8) {
            return new Vote(Vote.Label.ABSTAIN, 0.0, "eyes_not_measurable");
        }
        Collections.sort(ear);
        double range = ear.get(ear.size() - 2) - ear.get(1);
        String why = String.format(java.util.Locale.ROOT, "ear_range=%.3f over %.1fs", range, span / 1e9);
        if (range >= minRange) {
            return new Vote(Vote.Label.LIVE, 0.9, why);
        }
        return new Vote(Vote.Label.ABSTAIN, 0.0, "no_eyelid_motion " + why);
    }
}
