// #13 (WS1) | Bible §6, §9 | Java 11 only. Frozen API: changes need a BUILD_LOG.md note.
package io.sajeev.api;

import java.util.Optional;

/** A stream of frames from an open device (Frame Guard reads from this). */
public interface FrameSource extends AutoCloseable {

    /**
     * Wait for the next frame.
     * @param timeoutMillis how long to wait
     * @return the frame, or empty if none arrived in time
     * @throws DeviceException with DEVICE_DISCONNECTED or INVALID_FRAME_DATA
     */
    Optional<Frame> next(long timeoutMillis) throws DeviceException;

    /** @return current stream health */
    StreamHealth health();

    @Override
    void close();
}
