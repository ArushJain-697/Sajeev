// #58 (WS3) | Decode a frame and detect its face once, then share the result between the engine and the voters. Java 11 only.
// Before this, the texture voter, the moire voter and the re-evaluation each decoded the frame and ran the detector again.
package io.sajeev.face;

import ai.onnxruntime.OrtException;
import io.sajeev.api.Frame;
import io.sajeev.image.ImageCodec;
import io.sajeev.image.PixelImage;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/** A small cache keyed by the Frame object (identity), holding the last few frames. */
public final class FrameAnalysis {

    private static final class Entry {
        PixelImage image;
        List<Detection> faces;
    }

    private final YuNetDetector detector;
    private final int capacity;
    private final Map<Frame, Entry> cache = new IdentityHashMap<>();
    private final LinkedList<Frame> order = new LinkedList<>();

    /**
     * @param detector the face detector
     * @param capacity how many frames to remember (8 covers the passive window)
     */
    public FrameAnalysis(YuNetDetector detector, int capacity) {
        this.detector = detector;
        this.capacity = Math.max(1, capacity);
    }

    private synchronized Entry entry(Frame f) {
        Entry e = cache.get(f);
        if (e == null) {
            e = new Entry();
            e.image = ImageCodec.decode(f.jpeg());
            cache.put(f, e);
            order.addLast(f);
            while (order.size() > capacity) {
                cache.remove(order.removeFirst());
            }
        }
        return e;
    }

    /** @return the decoded image, or null when the frame cannot be decoded */
    public PixelImage image(Frame f) {
        return entry(f).image;
    }

    /** @return the faces found in the frame (empty when undecodable); detection runs once per frame */
    public synchronized List<Detection> faces(Frame f) throws OrtException {
        Entry e = entry(f);
        if (e.faces == null) {
            e.faces = e.image == null ? new ArrayList<Detection>() : detector.detect(e.image, 0.6, 0.3);
        }
        return e.faces;
    }

    /** Forget everything (called when a session ends, so no frame stays referenced). */
    public synchronized void clear() {
        cache.clear();
        order.clear();
    }
}
