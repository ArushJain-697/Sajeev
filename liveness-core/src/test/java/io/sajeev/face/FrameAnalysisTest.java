// #58 (WS3) | A frame is decoded and its face detected once, however many voters look at it.
package io.sajeev.face;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.sajeev.api.Frame;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class FrameAnalysisTest {

    @Test
    void test_frame_is_decoded_and_detected_once() throws Exception {
        String dir = System.getProperty("sajeev.models", System.getenv("SAJEEV_MODELS"));
        assumeTrue(dir != null, "set -Dsajeev.models=<dir>");
        try (YuNetDetector det = new YuNetDetector(Paths.get(dir, "yunet", "face_detection_yunet_2023mar.onnx"))) {
            FrameAnalysis a = new FrameAnalysis(det, 2);
            Frame f = new Frame(Files.readAllBytes(Paths.get("../testdata/fixtures-faces/one_face.jpg")), 0, 0);
            assertSame(a.image(f), a.image(f), "decoded twice");
            assertSame(a.faces(f), a.faces(f), "detected twice");
            assertEquals(1, a.faces(f).size());
            // the cache holds only the last frames, and clear() forgets everything
            Frame g = new Frame(f.jpeg(), 1, 1);
            Frame h = new Frame(f.jpeg(), 2, 2);
            Object first = a.image(f);
            a.image(g);
            a.image(h);
            assertNotSame(first, a.image(f), "the oldest frame should have been evicted from a cache of 2");
            Object before = a.image(h);
            a.clear();
            assertNotSame(before, a.image(h));
        }
    }
}
