// #15 (WS1) | Bible §6.1 | Frame Guard: measured fps, stall, bad frames, bursty arrival, processing mode.
// Java 11 only. Time comes from an injected clock in nanoseconds (System.nanoTime in production).
package io.sajeev.frame;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Watches one frame stream. Records arrivals and decode results, and raises {@link FrameFlag}s.
 * Measured fps is the median gap between arrivals over a 2-second window (bursty-safe).
 */
public final class FrameGuard {

    /** Window used for fps, bad-frame ratio and bursty checks (Bible §6.1: 2 s). */
    static final long WINDOW_NANOS = 2_000_000_000L;
    /** Stall limit (Bible §6.1: no frame for 2 s). */
    static final long STALL_NANOS = 2_000_000_000L;
    /** Bad-frame ratio that raises INVALID_FRAME_DATA (more than 20%). */
    static final double BAD_RATIO_LIMIT = 0.20;
    /** Minimum gaps before the bursty check means anything. */
    static final int MIN_GAPS_FOR_BURSTY = 4;

    private final LongSupplier clock;
    private final double minFps;
    private final ProcessingMode mode;
    private final int everyN;
    private final Deque<long[]> arrivals = new ArrayDeque<>(); // {nanos, decodedOk ? 1 : 0}
    private long lastArrival = -1L;
    private long seen;

    /**
     * @param clock nanosecond clock (monotonic)
     * @param minFps the configured minimum fps (liveness.minFps)
     * @param mode CONTINUOUS or PERIODIC
     * @param everyN for PERIODIC: process every Nth frame (ignored for CONTINUOUS); must be >= 1
     */
    public FrameGuard(LongSupplier clock, double minFps, ProcessingMode mode, int everyN) {
        if (everyN < 1) {
            throw new IllegalArgumentException("everyN must be >= 1");
        }
        this.clock = clock;
        this.minFps = minFps;
        this.mode = mode;
        this.everyN = everyN;
    }

    /**
     * Record one arrival.
     * @param decodedOk true if the frame decoded, false if it was bad or incomplete
     * @return true if this frame should be processed (CONTINUOUS: always; PERIODIC: every Nth)
     */
    public boolean onArrival(boolean decodedOk) {
        long now = clock.getAsLong();
        lastArrival = now;
        arrivals.addLast(new long[] {now, decodedOk ? 1L : 0L});
        trim(now);
        seen++;
        return mode == ProcessingMode.CONTINUOUS || (seen - 1) % everyN == 0;
    }

    /** @return measured fps over the window, or 0 when fewer than 2 arrivals */
    public double measuredFps() {
        List<Long> gaps = gaps(clock.getAsLong());
        if (gaps.isEmpty()) {
            return 0.0;
        }
        long median = median(gaps);
        return median <= 0L ? 0.0 : 1_000_000_000.0 / median;
    }

    /** @return the fraction of bad frames in the window, 0 when empty */
    public double badRatio() {
        trim(clock.getAsLong());
        if (arrivals.isEmpty()) {
            return 0.0;
        }
        long bad = 0;
        for (long[] a : arrivals) {
            if (a[1] == 0L) {
                bad++;
            }
        }
        return (double) bad / arrivals.size();
    }

    /** @return the current flags. Empty set when the stream is healthy. */
    public Set<FrameFlag> flags() {
        long now = clock.getAsLong();
        Set<FrameFlag> out = EnumSet.noneOf(FrameFlag.class);
        if (lastArrival >= 0L && now - lastArrival >= STALL_NANOS) {
            out.add(FrameFlag.STREAM_STALLED);
        }
        if (badRatio() > BAD_RATIO_LIMIT) {
            out.add(FrameFlag.INVALID_FRAME_DATA);
        }
        if (seen >= 2 && measuredFps() < minFps) {
            out.add(FrameFlag.DEVICE_INADEQUATE);
        }
        if (isBursty(now)) {
            out.add(FrameFlag.STREAM_BURSTY);
        }
        return Collections.unmodifiableSet(out);
    }

    /**
     * Bursty = a steady stream has gaps close to the median. Clumped delivery has many gaps far from it
     * (more than 20% outside [0.5x, 2x] of the median) and at least one gap of 3x the median.
     */
    private boolean isBursty(long now) {
        List<Long> gaps = gaps(now);
        if (gaps.size() < MIN_GAPS_FOR_BURSTY) {
            return false;
        }
        long median = median(gaps);
        if (median <= 0L) {
            return false;
        }
        int far = 0;
        long max = 0L;
        for (long g : gaps) {
            if (g * 2 < median || g > 2 * median) {
                far++;
            }
            max = Math.max(max, g);
        }
        return far * 5 > gaps.size() && max >= 3 * median;
    }

    private List<Long> gaps(long now) {
        trim(now);
        List<Long> gaps = new ArrayList<>();
        long prev = -1L;
        for (long[] a : arrivals) {
            if (prev >= 0L) {
                gaps.add(a[0] - prev);
            }
            prev = a[0];
        }
        return gaps;
    }

    private void trim(long now) {
        while (!arrivals.isEmpty() && now - arrivals.peekFirst()[0] > WINDOW_NANOS) {
            arrivals.removeFirst();
        }
    }

    private static long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }
}
