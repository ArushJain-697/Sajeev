// #15 (WS1) | Bible §6.1 | Frame health flags. Java 11 only.
package io.sajeev.frame;

/** Health flags raised by Frame Guard. Each maps to a PDF error state or a diagnostic. */
public enum FrameFlag {
    /** No frame for longer than the stall limit (2 s). Maps to a device error. */
    STREAM_STALLED,
    /** More than 20% bad frames in the last 2 s. Maps to INVALID_FRAME_DATA. */
    INVALID_FRAME_DATA,
    /** Measured fps is below the configured minimum. Maps to DEVICE_INADEQUATE. */
    DEVICE_INADEQUATE,
    /** Frames arrive in clumps (bursty TCP delivery). Diagnostic only; fps is then less reliable. */
    STREAM_BURSTY
}
