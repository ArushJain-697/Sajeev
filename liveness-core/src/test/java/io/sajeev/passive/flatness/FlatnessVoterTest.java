// #20 (WS1) | Bible §6.4 V3 | Tests named as claims. Landmarks come from the real landmark model on a public-domain photo;
// the warp and the head turn are SYNTHETIC transforms of those landmarks (DEV recordings of real turns are pending, #59/#62).
package io.sajeev.passive.flatness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.sajeev.api.Vote;
import io.sajeev.face.Detection;
import io.sajeev.face.FaceFeatures;
import io.sajeev.face.LandmarkModel;
import io.sajeev.face.YuNetDetector;
import io.sajeev.image.AwtDecoder;
import io.sajeev.image.PixelImage;
import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class FlatnessVoterTest {

    private static final double MIN_YAW = 0.14;
    private final FlatnessVoter voter = new FlatnessVoter(MIN_YAW, 0.02, 200, 0.03, 42L);

    private static float[][] base() throws Exception {
        String dir = System.getProperty("sajeev.models", System.getenv("SAJEEV_MODELS"));
        assumeTrue(dir != null, "set -Dsajeev.models=<dir>");
        PixelImage img = AwtDecoder.from(ImageIO.read(new File("../testdata/fixtures-faces/one_face.jpg")));
        try (YuNetDetector det = new YuNetDetector(Paths.get(dir, "yunet", "face_detection_yunet_2023mar.onnx"));
             LandmarkModel lm = new LandmarkModel(Paths.get(dir, "landmark", "face_landmarks_detector.onnx"))) {
            List<Detection> faces = det.detect(img, 0.6, 0.3);
            return lm.landmarks(img, faces.get(0));
        }
    }

    /** A flat picture tilted away: one projective map applied to every landmark, plus 0.3 px of jitter. */
    private static float[][] planarWarp(float[][] p, double tilt) {
        float[][] q = new float[p.length][3];
        double cx = (p[234][0] + p[454][0]) / 2.0;
        double cy = (p[10][1] + p[152][1]) / 2.0;
        double width = Math.abs(p[454][0] - p[234][0]);
        Random r = new Random(7);
        for (int i = 0; i < p.length; i++) {
            double w = 1.0 + tilt * (p[i][0] - cx) / width;
            q[i][0] = (float) (cx + (p[i][0] - cx) / w + 0.3 * r.nextGaussian());
            q[i][1] = (float) (cy + (p[i][1] - cy) / w + 0.3 * r.nextGaussian());
            q[i][2] = p[i][2];
        }
        return q;
    }

    /** A real head: the landmarks keep their depth and the head turns about a vertical axis (orthographic view). */
    private static float[][] headTurn(float[][] p, double degrees) {
        double width = Math.abs(p[454][0] - p[234][0]);
        double cx = (p[234][0] + p[454][0]) / 2.0;
        float[] zs = new float[p.length];
        for (int i = 0; i < p.length; i++) {
            zs[i] = p[i][2];
        }
        float[] sorted = zs.clone();
        Arrays.sort(sorted);
        double range = sorted[sorted.length - 1] - sorted[0];
        double median = sorted[sorted.length / 2];
        double scale = 0.5 * width / range; // a head is about half as deep as the face is wide, across the mesh
        double th = Math.toRadians(degrees);
        float[][] q = new float[p.length][3];
        for (int i = 0; i < p.length; i++) {
            double z = (zs[i] - median) * scale; // model z grows away from the camera
            double x = p[i][0] - cx;
            q[i][0] = (float) (cx + x * Math.cos(th) + z * Math.sin(th));
            q[i][1] = p[i][1];
            q[i][2] = p[i][2];
        }
        return q;
    }

    @Test
    void test_planar_warp_of_a_face_votes_attack() throws Exception {
        float[][] a = base();
        float[][] b = planarWarp(a, 0.8);
        assertTrue(Math.abs(FaceFeatures.yaw(a) - FaceFeatures.yaw(b)) >= MIN_YAW, "the warp must change yaw enough");
        Vote v = voter.vote(List.of(a, b));
        assertEquals(Vote.Label.ATTACK, v.label(), v.reason());
    }

    @Test
    void test_real_head_turn_votes_live() throws Exception {
        float[][] a = base();
        float[][] b = headTurn(a, 18);
        assertTrue(Math.abs(FaceFeatures.yaw(a) - FaceFeatures.yaw(b)) >= MIN_YAW, "the turn must change yaw enough");
        Vote v = voter.vote(List.of(a, b));
        assertEquals(Vote.Label.LIVE, v.label(), v.reason() + " residual=" + voter.residual(a, b));
    }

    @Test
    void test_zero_motion_abstains_never_live() throws Exception {
        float[][] a = base();
        assertEquals(Vote.Label.ABSTAIN, voter.vote(List.of(a, a, a)).label());
        assertEquals(Vote.Label.ABSTAIN, voter.vote(java.util.Collections.singletonList(a)).label());
        assertEquals(Vote.Label.ABSTAIN, voter.vote(List.of(a, headTurn(a, 3))).label()); // below 8 degrees
    }

    @Test
    void test_homography_direction_does_not_change_the_label() throws Exception {
        float[][] a = base();
        float[][] flat = planarWarp(a, 0.8);
        float[][] turn = headTurn(a, 18);
        assertEquals(voter.vote(List.of(a, flat)).label(), voter.vote(List.of(flat, a)).label());
        assertEquals(voter.vote(List.of(a, turn)).label(), voter.vote(List.of(turn, a)).label());
    }

    @Test
    void test_result_is_repeatable_with_the_fixed_seed() throws Exception {
        float[][] a = base();
        float[][] b = headTurn(a, 18);
        assertEquals(voter.residual(a, b), voter.residual(a, b), 0.0);
    }

    @Test
    void test_residual_does_not_depend_on_face_size() throws Exception {
        float[][] a = base();
        float[][] b = headTurn(a, 18);
        float[][] a2 = new float[a.length][3];
        float[][] b2 = new float[b.length][3];
        for (int i = 0; i < a.length; i++) {
            for (int k = 0; k < 2; k++) {
                a2[i][k] = a[i][k] * 2.5f;
                b2[i][k] = b[i][k] * 2.5f;
            }
        }
        assertEquals(voter.residual(a, b), voter.residual(a2, b2), 1e-3);
    }

    @Test
    void test_the_two_cases_are_well_apart_not_just_either_side_of_the_threshold() throws Exception {
        float[][] a = base();
        double flat = voter.residual(a, planarWarp(a, 0.8));
        double real = voter.residual(a, headTurn(a, 18));
        System.out.println("V3 residuals (inter-ocular units): planar=" + flat + " real turn=" + real);
        assertTrue(flat < 0.01, "planar residual " + flat);
        assertTrue(real > 0.04, "real-turn residual " + real);
    }
}
