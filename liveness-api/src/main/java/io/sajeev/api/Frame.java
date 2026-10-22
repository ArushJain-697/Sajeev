// #13 (WS1) | Bible §6, §9 | Java 11 only. Frozen API: changes need a BUILD_LOG.md note.
package io.sajeev.api;

import java.util.Arrays;

/** One video frame: JPEG bytes, a monotonic arrival time and a sequence number. Immutable. */
public final class Frame {
    private final byte[] jpeg;
    private final long arrivalNanos;
    private final long sequence;

    /**
     * @param jpeg the encoded frame; copied on the way in
     * @param arrivalNanos time from a monotonic clock (never wall-clock time)
     * @param sequence increasing frame number from the source
     */
    public Frame(byte[] jpeg, long arrivalNanos, long sequence) {
        if (jpeg == null || jpeg.length == 0) {
            throw new IllegalArgumentException("jpeg must not be empty");
        }
        this.jpeg = Arrays.copyOf(jpeg, jpeg.length);
        this.arrivalNanos = arrivalNanos;
        this.sequence = sequence;
    }

    /** @return a copy of the JPEG bytes */
    public byte[] jpeg() {
        return Arrays.copyOf(jpeg, jpeg.length);
    }

    /** @return monotonic arrival time in nanoseconds */
    public long arrivalNanos() {
        return arrivalNanos;
    }

    /** @return the sequence number */
    public long sequence() {
        return sequence;
    }
}
