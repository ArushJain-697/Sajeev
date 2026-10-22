// #25 (WS1) | Bible §6.6 | One detector per challenge type. Each is stateful: feed it frames in order. Java 11 only.
// Every action except a blink must be HELD for several frames. "Left" means the person's own left.
package io.sajeev.active;

import io.sajeev.api.ChallengeType;
import java.util.List;

/** Builds the detector for a challenge type. A detector returns true on the frame where the action is confirmed. */
public final class ActionDetectors {

    /** A stateful detector. */
    public interface Detector {
        /** @param f the next frame's features @return true once the action is confirmed (and on later frames until reset) */
        boolean update(FrameFeatures f);
    }

    /** The person's neutral values, from the first second of frames. */
    public static final class Baseline {
        final double yaw;
        final double pitch;
        final double ear;
        final double mouthAperture;
        final double mouthWidth;
        final double gazeX;
        final double gazeY;

        private Baseline(double[] m) {
            yaw = m[0]; pitch = m[1]; ear = m[2]; mouthAperture = m[3]; mouthWidth = m[4]; gazeX = m[5]; gazeY = m[6];
        }

        /**
         * Median of each feature over the frames of the first second.
         * @param frames at least one frame
         * @return the baseline
         */
        public static Baseline of(List<FrameFeatures> frames) {
            if (frames.isEmpty()) {
                throw new IllegalArgumentException("need at least one frame for a baseline");
            }
            double[][] cols = new double[7][frames.size()];
            for (int i = 0; i < frames.size(); i++) {
                FrameFeatures f = frames.get(i);
                cols[0][i] = f.yaw(); cols[1][i] = f.pitch(); cols[2][i] = f.ear(); cols[3][i] = f.mouthAperture();
                cols[4][i] = f.mouthWidth(); cols[5][i] = f.gazeX(); cols[6][i] = f.gazeY();
            }
            double[] m = new double[7];
            for (int c = 0; c < 7; c++) {
                java.util.Arrays.sort(cols[c]);
                m[c] = cols[c][cols[c].length / 2];
            }
            return new Baseline(m);
        }
    }

    private ActionDetectors() {
    }

    /** Counts consecutive frames where a condition holds. */
    private abstract static class Held implements Detector {
        private final int need;
        private int run;

        Held(int need) {
            this.need = need;
        }

        abstract boolean holds(FrameFeatures f);

        @Override
        public boolean update(FrameFeatures f) {
            run = holds(f) ? run + 1 : 0;
            return run >= need;
        }
    }

    /**
     * @param type the challenge
     * @param b the person's neutral values
     * @param t the limits
     * @return a fresh detector for that challenge
     */
    public static Detector create(ChallengeType type, Baseline b, ActionThresholds t) {
        final int n = t.heldFrames;
        switch (type) {
            case SMILE:
                return new Held(n) {
                    boolean holds(FrameFeatures f) { return b.mouthWidth > 0 && f.mouthWidth() / b.mouthWidth >= t.smileRatio; }
                };
            case OPEN_MOUTH:
                return new Held(n) {
                    boolean holds(FrameFeatures f) { return f.mouthAperture() - b.mouthAperture >= t.mouthOpenRise; }
                };
            case TURN_HEAD_LEFT:
                return new Held(n) {
                    boolean holds(FrameFeatures f) { return f.yaw() - b.yaw >= t.yawChange; }
                };
            case TURN_HEAD_RIGHT:
                return new Held(n) {
                    boolean holds(FrameFeatures f) { return b.yaw - f.yaw() >= t.yawChange; }
                };
            case LOOK_LEFT:
                return new Held(n) {
                    boolean holds(FrameFeatures f) { return f.gazeX() - b.gazeX >= t.gazeHorizontal; }
                };
            case LOOK_RIGHT:
                return new Held(n) {
                    boolean holds(FrameFeatures f) { return b.gazeX - f.gazeX() >= t.gazeHorizontal; }
                };
            case LOOK_UP: // gaze up AND head pitch up: gaze alone is not enough
                return new Held(n) {
                    boolean holds(FrameFeatures f) {
                        return b.gazeY - f.gazeY() >= t.gazeUp && b.pitch - f.pitch() >= t.pitchChange;
                    }
                };
            case LOOK_DOWN:
                return new Held(n) {
                    boolean holds(FrameFeatures f) {
                        return f.gazeY() - b.gazeY >= t.gazeDown && f.pitch() - b.pitch >= t.pitchChange;
                    }
                };
            case EYES_CLOSED_HOLD:
                return new Detector() {
                    private int closedRun;
                    private long closedSince = -1;
                    private boolean closedLongEnough;

                    @Override
                    public boolean update(FrameFeatures f) {
                        if (f.ear() < t.earClosed) {
                            if (closedRun == 0) {
                                closedSince = f.arrivalNanos();
                            }
                            closedRun++;
                            // closed for enough frames AND long enough in time: a normal blink (about 0.2 s) is not a hold
                            if (closedRun >= n && f.arrivalNanos() - closedSince >= t.closedHoldMillis * 1_000_000L) {
                                closedLongEnough = true;
                            }
                        } else {
                            if (closedLongEnough) {
                                return true; // closed long enough, then reopened
                            }
                            closedRun = 0;
                        }
                        return false;
                    }
                };
            case BLINK:
                return new Detector() {
                    private boolean dipped;

                    @Override
                    public boolean update(FrameFeatures f) {
                        if (f.ear() <= b.ear * t.blinkDip) {
                            dipped = true;
                        } else if (dipped && f.ear() >= b.ear * 0.85) {
                            return true; // one dip then recovery: a blink (not held)
                        }
                        return false;
                    }
                };
            default:
                throw new IllegalArgumentException("no detector for " + type);
        }
    }
}
