// #17 (WS1) | Bible §6.2 | Tests named as claims. Needs the landmark and YuNet models (-Dsajeev.models=<dir>).
// The gaze and closed-eye tests below use SYNTHETIC fixtures (testdata/fixtures-gaze), not real people (see BUILD_LOG #17).
package io.sajeev.face;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.sajeev.image.AwtDecoder;
import io.sajeev.image.PixelImage;
import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class LandmarkFeaturesTest {

    private static String root() {
        String dir = System.getProperty("sajeev.models", System.getenv("SAJEEV_MODELS"));
        assumeTrue(dir != null, "set -Dsajeev.models=<dir with yunet/ and landmark/>");
        return dir;
    }

    private static PixelImage mirror(PixelImage src) {
        PixelImage out = new PixelImage(src.getWidth(), src.getHeight());
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                out.setRGB(src.getWidth() - 1 - x, y, src.getRGB(x, y));
            }
        }
        return out;
    }

    private static float[][] landmarksOf(String dir, PixelImage img) throws Exception {
        try (YuNetDetector det = new YuNetDetector(Paths.get(dir, "yunet", "face_detection_yunet_2023mar.onnx"));
             LandmarkModel lm = new LandmarkModel(Paths.get(dir, "landmark", "face_landmarks_detector.onnx"))) {
            List<Detection> faces = det.detect(img, 0.6, 0.3);
            assertEquals(1, faces.size(), "expected one face");
            return lm.landmarks(img, faces.get(0));
        }
    }

    @Test
    void test_landmark_model_returns_478_points_inside_the_frame() throws Exception {
        String dir = root();
        PixelImage img = AwtDecoder.from(ImageIO.read(new File("../testdata/fixtures-faces/one_face.jpg")));
        float[][] p = landmarksOf(dir, img);
        assertEquals(LandmarkModel.POINTS, p.length);
        int inside = 0;
        for (float[] q : p) {
            if (q[0] >= 0 && q[0] < img.getWidth() && q[1] >= 0 && q[1] < img.getHeight()) {
                inside++;
            }
        }
        assertTrue(inside > 0.95 * p.length, "only " + inside + " of " + p.length + " points inside the frame");
    }

    @Test
    void test_ear_is_open_for_an_open_eye() throws Exception {
        PixelImage img = AwtDecoder.from(ImageIO.read(new File("../testdata/fixtures-faces/one_face.jpg")));
        float[][] p = landmarksOf(root(), img);
        double l = FaceFeatures.eyeAspectRatio(p, FaceFeatures.EYE_LEFT);
        double r = FaceFeatures.eyeAspectRatio(p, FaceFeatures.EYE_RIGHT);
        assertTrue(l > 0.15 && r > 0.15, "EAR left " + l + " right " + r + " (open eyes should be above 0.15)");
    }

    @Test
    void test_yaw_sign_matches_users_left_on_mirrored_fixture() throws Exception {
        String dir = root();
        PixelImage img = AwtDecoder.from(ImageIO.read(new File("../testdata/fixtures-faces/one_face.jpg")));
        double y = FaceFeatures.yaw(landmarksOf(dir, img));
        double ym = FaceFeatures.yaw(landmarksOf(dir, mirror(img)));
        // A mirror turns the face the other way: the sign must flip (tolerance for a small detection difference)
        assertTrue(y * ym < 0, "yaw " + y + " and mirrored yaw " + ym + " should have opposite signs");
        assertTrue(Math.abs(y + ym) < 0.15, "yaw " + y + " and mirrored yaw " + ym + " should be equal and opposite");
    }

    private static float[][] landmarksFile(String file) throws Exception {
        PixelImage img = AwtDecoder.from(ImageIO.read(new File("../testdata/fixtures-gaze/" + file)));
        return landmarksOf(root(), img);
    }

    @Test
    void test_gaze_direction_on_four_direction_fixtures() throws Exception {
        // SYNTHETIC fixtures. TARGET limits: horizontal 0.07, up 0.04, down 0.02 of the eye width.
        double[] neutral = FaceFeatures.gaze(landmarksFile("neutral.jpg"));
        assertEquals(FaceFeatures.Gaze.LEFT,
                FaceFeatures.classifyGaze(FaceFeatures.gaze(landmarksFile("look_left.jpg")), neutral, 0.07, 0.04, 0.02));
        assertEquals(FaceFeatures.Gaze.RIGHT,
                FaceFeatures.classifyGaze(FaceFeatures.gaze(landmarksFile("look_right.jpg")), neutral, 0.07, 0.04, 0.02));
        assertEquals(FaceFeatures.Gaze.UP,
                FaceFeatures.classifyGaze(FaceFeatures.gaze(landmarksFile("look_up.jpg")), neutral, 0.07, 0.04, 0.02));
        assertEquals(FaceFeatures.Gaze.DOWN,
                FaceFeatures.classifyGaze(FaceFeatures.gaze(landmarksFile("look_down.jpg")), neutral, 0.07, 0.04, 0.02));
        assertEquals(FaceFeatures.Gaze.CENTER,
                FaceFeatures.classifyGaze(neutral, neutral, 0.07, 0.04, 0.02));
    }

    @Test
    void test_ear_drops_when_eyes_closed() throws Exception {
        // SYNTHETIC: eyes painted over. Open EAR is about 0.25, closed about 0.05.
        float[][] open = landmarksFile("neutral.jpg");
        float[][] closed = landmarksFile("eyes_closed.jpg");
        assertTrue(FaceFeatures.eyeAspectRatio(closed, FaceFeatures.EYE_LEFT) < 0.5 * FaceFeatures.eyeAspectRatio(open, FaceFeatures.EYE_LEFT));
        assertTrue(FaceFeatures.eyesClosed(closed, 0.12));
        assertTrue(!FaceFeatures.eyesClosed(open, 0.12));
    }
}
