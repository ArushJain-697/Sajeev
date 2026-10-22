// #15 (WS1) | Bible §6.1 FileAdapter | MOCK: replays recorded JPEG frames at a set fps. Java 11 only.
// The engine labels every session that uses this source as MOCK. It is for tests, the bench and evaluation.
package io.sajeev.devices;

import io.sajeev.api.DeviceException;
import io.sajeev.api.ErrorState;
import io.sajeev.api.Frame;
import io.sajeev.api.FrameSource;
import io.sajeev.api.StreamHealth;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * MOCK frame source: replays a folder of .jpg frames in name order at a fixed fps.
 * Arrival times are synthetic (sequence / fps), so replays are deterministic. Not a real device.
 */
public final class FileFrameSource implements FrameSource {

    private final List<byte[]> frames;
    private final long stepNanos;
    private int next;
    private boolean closed;

    /**
     * @param dir folder with .jpg files (at least one)
     * @param fps the replay frame rate, > 0
     * @throws DeviceException DEVICE_UNAVAILABLE if the folder cannot be read or is empty
     */
    public FileFrameSource(Path dir, double fps) throws DeviceException {
        if (fps <= 0.0) {
            throw new IllegalArgumentException("fps must be > 0");
        }
        this.stepNanos = (long) (1_000_000_000.0 / fps);
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> files = s.filter(p -> p.toString().toLowerCase().endsWith(".jpg"))
                    .sorted()
                    .collect(Collectors.toList());
            if (files.isEmpty()) {
                throw new DeviceException(ErrorState.DEVICE_UNAVAILABLE, "no .jpg frames in " + dir);
            }
            this.frames = new ArrayList<>();
            for (Path p : files) {
                frames.add(Files.readAllBytes(p));
            }
        } catch (IOException e) {
            throw new DeviceException(ErrorState.DEVICE_UNAVAILABLE, "cannot read " + dir + ": " + e.getMessage());
        }
    }

    @Override
    public Optional<Frame> next(long timeoutMillis) throws DeviceException {
        if (closed) {
            throw new DeviceException(ErrorState.DEVICE_DISCONNECTED, "source closed");
        }
        if (next >= frames.size()) {
            return Optional.empty();
        }
        long seq = next;
        Frame f = new Frame(frames.get(next), seq * stepNanos, seq);
        next++;
        return Optional.of(f);
    }

    @Override
    public StreamHealth health() {
        double fps = 1_000_000_000.0 / stepNanos;
        return new StreamHealth(fps, next, 0L);
    }

    @Override
    public void close() {
        closed = true;
    }
}
