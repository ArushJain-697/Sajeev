// #13 (WS1) | Bible §6, §9 | Java 11 only. Frozen API: changes need a BUILD_LOG.md note.
package io.sajeev.api;

/** Live health of a frame stream, used by Frame Guard (Bible §6.1). Immutable. */
public final class StreamHealth {
    private final double measuredFps;
    private final long framesSeen;
    private final long gapsOverLimit;

    /**
     * @param measuredFps frames per second measured over the recent window
     * @param framesSeen total frames received in this session
     * @param gapsOverLimit number of gaps longer than the configured limit
     */
    public StreamHealth(double measuredFps, long framesSeen, long gapsOverLimit) {
        this.measuredFps = measuredFps;
        this.framesSeen = framesSeen;
        this.gapsOverLimit = gapsOverLimit;
    }

    /** @return measured frames per second */
    public double measuredFps() {
        return measuredFps;
    }

    /** @return total frames seen */
    public long framesSeen() {
        return framesSeen;
    }

    /** @return gaps longer than the configured limit */
    public long gapsOverLimit() {
        return gapsOverLimit;
    }
}
