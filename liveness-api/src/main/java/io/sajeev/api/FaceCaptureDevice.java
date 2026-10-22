// #13 (WS1) | Bible §6, §9 | Java 11 only. Frozen API: changes need a BUILD_LOG.md note.
package io.sajeev.api;

import java.util.List;
import java.util.Optional;

/**
 * Common face capture device interface (PDF IN1). Adapters implement it for SBI 1.0 HTTP,
 * SBI 0.9.5, the Android SBI stream extension, CameraX and files (IN2).
 */
public interface FaceCaptureDevice {

    /** @return devices currently visible to this adapter (may be empty) */
    List<DeviceInfo> discover();

    /** @param deviceId an id returned by discover() @return the device info, if known */
    Optional<DeviceInfo> info(String deviceId);

    /** @param deviceId an id returned by discover() @return what the device can do */
    DeviceCapabilities capabilities(String deviceId);

    /**
     * Open a frame stream from the device.
     * @param deviceId an id returned by discover()
     * @return a source of frames
     * @throws DeviceException with an ErrorState (DEVICE_UNAVAILABLE, CAMERA_UNAVAILABLE, ...)
     */
    FrameSource openStream(String deviceId) throws DeviceException;

    /**
     * Take one still capture (rCapture). Used when no stream is available: capture-only,
     * which the engine labels LIMITED.
     * @param deviceId an id returned by discover()
     * @return one frame
     * @throws DeviceException with an ErrorState
     */
    Frame capture(String deviceId) throws DeviceException;

    /** @param deviceId the device to release; safe to call more than once */
    void close(String deviceId);
}
