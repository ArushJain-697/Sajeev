// #16 (WS1) | Bible §6.2 | Tests named as claims. Needs the YuNet model; skipped with a message if it is absent.
package io.sajeev.face;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.sajeev.api.ErrorState;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class FaceTrackerTest {

    /** TARGET limits, measured on the synthetic fixtures; calibrated on DEV in #62. */
    private static final FaceTracker TRACKER = new FaceTracker(new FaceTracker.Limits(0.02, 40.0, 50.0));

    private static Path modelPath() {
        String dir = System.getProperty("sajeev.models", System.getenv("SAJEEV_MODELS"));
        assumeTrue(dir != null, "set -Dsajeev.models=<dir with face_detection_yunet_2023mar.onnx> to run");
        Path p = Paths.get(dir, "face_detection_yunet_2023mar.onnx");
        assumeTrue(Files.exists(p), "YuNet model not found at " + p);
        return p;
    }

    private static BufferedImage fixture(String name) throws Exception {
        File f = Paths.get("../testdata/fixtures-faces", name).toFile();
        return ImageIO.read(f);
    }

    private static FaceTracker.State run(String fixture) throws Exception {
        try (YuNetDetector det = new YuNetDetector(modelPath())) {
            BufferedImage img = fixture(fixture);
            List<Detection> found = det.detect(img, 0.6, 0.3);
            Detection box = found.size() == 1 ? found.get(0) : null;
            return TRACKER.assess(found, FaceQuality.measure(img, box));
        }
    }

    @Test
    void test_zero_faces_reports_not_detected() throws Exception {
        FaceTracker.State s = run("zero_faces.jpg");
        assertEquals(ErrorState.FACE_NOT_DETECTED, s.error().get());
    }

    @Test
    void test_one_face_passes_the_tracker() throws Exception {
        FaceTracker.State s = run("one_face.jpg");
        assertTrue(!s.error().isPresent(), "one face should pass, got " + s.reason());
        assertEquals("ok", s.reason());
    }

    @Test
    void test_two_faces_pauses_and_reports_multiple() throws Exception {
        FaceTracker.State s = run("two_faces_composite.jpg");
        assertEquals(ErrorState.MULTIPLE_FACES, s.error().get());
        assertEquals(null, s.face(), "the tracker must not pick one of two faces");
    }

    @Test
    void test_dark_frame_reports_poor_quality_with_reason() throws Exception {
        FaceTracker.State s = run("dark_one_face.jpg");
        assertEquals(ErrorState.POOR_QUALITY, s.error().get());
        assertEquals("dark", s.reason());
    }

    @Test
    void test_blurry_frame_loses_the_face_and_measures_as_blurry() throws Exception {
        // The detector finds no face in this blur. The frame is still measured: sharpness is far below the limit.
        FaceTracker.State s = run("blurry_one_face.jpg");
        assertEquals(ErrorState.FACE_NOT_DETECTED, s.error().get());
        FaceQuality.Metrics m = FaceQuality.measure(fixture("blurry_one_face.jpg"), null);
        assertTrue(m.sharpness() < 50.0, "sharpness " + m.sharpness());
    }
}
