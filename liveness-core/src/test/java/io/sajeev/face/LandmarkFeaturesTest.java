// #17 (WS1) | Bible §6.2 | Tests named as claims. Needs the landmark and YuNet models (-Dsajeev.models=<dir>).
// NOT written here (no fixtures yet): test_gaze_direction_on_four_direction_fixtures and test_ear_drops_when_eyes_closed (see BUILD_LOG #17).
package io.sajeev.face;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
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

    private static BufferedImage mirror(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                out.setRGB(src.getWidth() - 1 - x, y, src.getRGB(x, y));
            }
        }
        return out;
    }

    private static float[][] landmarksOf(String dir, BufferedImage img) throws Exception {
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
        BufferedImage img = ImageIO.read(new File("../testdata/fixtures-faces/one_face.jpg"));
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
        BufferedImage img = ImageIO.read(new File("../testdata/fixtures-faces/one_face.jpg"));
        float[][] p = landmarksOf(root(), img);
        double l = FaceFeatures.eyeAspectRatio(p, FaceFeatures.EYE_LEFT);
        double r = FaceFeatures.eyeAspectRatio(p, FaceFeatures.EYE_RIGHT);
        assertTrue(l > 0.15 && r > 0.15, "EAR left " + l + " right " + r + " (open eyes should be above 0.15)");
    }

    @Test
    void test_yaw_sign_matches_users_left_on_mirrored_fixture() throws Exception {
        String dir = root();
        BufferedImage img = ImageIO.read(new File("../testdata/fixtures-faces/one_face.jpg"));
        double y = FaceFeatures.yaw(landmarksOf(dir, img));
        double ym = FaceFeatures.yaw(landmarksOf(dir, mirror(img)));
        // A mirror turns the face the other way: the sign must flip (tolerance for a small detection difference)
        assertTrue(y * ym < 0, "yaw " + y + " and mirrored yaw " + ym + " should have opposite signs");
        assertTrue(Math.abs(y + ym) < 0.15, "yaw " + y + " and mirrored yaw " + ym + " should be equal and opposite");
    }
}
