// #15 (WS1) | Bible §6.1 | CONTINUOUS or PERIODIC processing (config: liveness.frameProcessing). Java 11 only.
package io.sajeev.frame;

/** Which frames the engine processes. */
public enum ProcessingMode {
    /** Every frame. */
    CONTINUOUS,
    /** Every Nth frame, where N is configured. */
    PERIODIC
}
