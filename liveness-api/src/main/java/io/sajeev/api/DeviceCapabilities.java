// #13 (WS1) | Bible §6, §9 | Java 11 only. Frozen API: changes need a BUILD_LOG.md note.
package io.sajeev.api;

/** What a capture device can do (PDF "device capability discovery"). Immutable. */
public final class DeviceCapabilities {
    private final boolean streamSupported;
    private final boolean captureSupported;
    private final int maxFps;

    /**
     * @param streamSupported the device offers a frame stream (/stream)
     * @param captureSupported the device offers single capture (rCapture)
     * @param maxFps the device's highest advertised frame rate, 0 if unknown
     */
    public DeviceCapabilities(boolean streamSupported, boolean captureSupported, int maxFps) {
        this.streamSupported = streamSupported;
        this.captureSupported = captureSupported;
        this.maxFps = maxFps;
    }

    /** @return true if a frame stream is available */
    public boolean streamSupported() {
        return streamSupported;
    }

    /** @return true if single capture is available */
    public boolean captureSupported() {
        return captureSupported;
    }

    /** @return the advertised maximum fps, 0 if unknown */
    public int maxFps() {
        return maxFps;
    }
}
