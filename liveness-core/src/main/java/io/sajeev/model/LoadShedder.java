// #33 (WS1) | PDF BN7/BN8, P-B | What to drop under load. The PAD voters are never skipped. Java 11 only.
package io.sajeev.model;

/**
 * Decides how to shed work when frames arrive faster than they are processed. Order of shedding:
 * (1) drop the oldest queued frames, (2) run the texture CNN less often, (3) turn the CNN off and run the classical
 * voters alone with a stricter gate. The classical PAD voters (V2 to V5) always run on every frame that is processed.
 */
public final class LoadShedder {

    /** Degradation levels (P-B ladder). */
    public enum Level { NORMAL, CNN_REDUCED, CLASSICAL_ONLY_STRICT }

    /** The plan for the next processing round. */
    public static final class Plan {
        private final int dropOldest;
        private final int cnnEveryNth;
        private final boolean padVotersRun;
        private final Level level;

        Plan(int dropOldest, int cnnEveryNth, boolean padVotersRun, Level level) {
            this.dropOldest = dropOldest;
            this.cnnEveryNth = cnnEveryNth;
            this.padVotersRun = padVotersRun;
            this.level = level;
        }

        /** @return how many of the oldest queued frames to drop */
        public int dropOldest() { return dropOldest; }

        /** @return run the texture CNN on every Nth processed frame; 0 means the CNN is off */
        public int cnnEveryNth() { return cnnEveryNth; }

        /** @return always true: the classical PAD voters are never skipped */
        public boolean padVotersRun() { return padVotersRun; }

        /** @return the degradation level */
        public Level level() { return level; }

        /** @return true when the gate must be stricter (CNN off, fewer voters) */
        public boolean strictGate() { return level == Level.CLASSICAL_ONLY_STRICT; }
    }

    private final int maxQueue;

    /** @param maxQueue the most frames allowed to wait before the oldest are dropped */
    public LoadShedder(int maxQueue) {
        if (maxQueue < 1) {
            throw new IllegalArgumentException("maxQueue must be >= 1");
        }
        this.maxQueue = maxQueue;
    }

    /**
     * @param queueDepth frames waiting
     * @param processingMs average time to process one frame, all voters
     * @param frameIntervalMs time between arriving frames
     * @param cnnAvailable false when the CNN model is missing or failed
     * @return the plan
     */
    public Plan plan(int queueDepth, double processingMs, double frameIntervalMs, boolean cnnAvailable) {
        int drop = Math.max(0, queueDepth - maxQueue);
        if (!cnnAvailable) {
            return new Plan(drop, 0, true, Level.CLASSICAL_ONLY_STRICT);
        }
        double load = frameIntervalMs <= 0 ? Double.MAX_VALUE : processingMs / frameIntervalMs;
        if (load <= 1.0 && drop == 0) {
            return new Plan(0, 1, true, Level.NORMAL);
        }
        if (load <= 2.5) {
            return new Plan(drop, 3, true, Level.CNN_REDUCED); // CNN on every third processed frame
        }
        return new Plan(drop, 0, true, Level.CLASSICAL_ONLY_STRICT); // overload: shed the CNN, keep the PAD voters
    }
}
