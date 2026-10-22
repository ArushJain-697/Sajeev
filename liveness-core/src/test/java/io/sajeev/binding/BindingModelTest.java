// #27 (WS1) | Real-model tests: YuNet + landmarks + alignment + SFace on the one public-domain face photo.
// Needs -Dsajeev.models=<dir with yunet/, landmark/, sface/>. There is only ONE real person in our fixtures, so these
// tests show "the same face survives re-encoding" and "noise is rejected"; they do NOT show a different person is rejected.
package io.sajeev.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.sajeev.face.Detection;
import io.sajeev.face.LandmarkModel;
import io.sajeev.face.YuNetDetector;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import org.junit.jupiter.api.Test;

class BindingModelTest {

    private static Path modelsRoot() {
        String dir = System.getProperty("sajeev.models", System.getenv("SAJEEV_MODELS"));
        assumeTrue(dir != null, "set -Dsajeev.models");
        Path sface = Paths.get(dir, "sface", "face_recognition_sface_2021dec.onnx");
        assumeTrue(Files.exists(sface), "SFace model missing");
        return Paths.get(dir);
    }

    /** Re-encode: scale to 80%, darken a little, JPEG quality 0.5. A different size and compression, like a stored capture. */
    private static BufferedImage reencode(BufferedImage src) throws Exception {
        int w = (int) (src.getWidth() * 0.8);
        int h = (int) (src.getHeight() * 0.8);
        BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = small.getRGB(x, y);
                int r = (int) (((rgb >> 16) & 0xFF) * 0.9);
                int gg = (int) (((rgb >> 8) & 0xFF) * 0.9);
                int b = (int) ((rgb & 0xFF) * 0.9);
                small.setRGB(x, y, (r << 16) | (gg << 8) | b);
            }
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.5f);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        writer.setOutput(ImageIO.createImageOutputStream(bos));
        writer.write(null, new IIOImage(small, null, null), param);
        writer.dispose();
        return ImageIO.read(new ByteArrayInputStream(bos.toByteArray()));
    }

    private static float[][] landmarks(Path root, BufferedImage img) throws Exception {
        try (YuNetDetector det = new YuNetDetector(root.resolve("yunet/face_detection_yunet_2023mar.onnx"));
             LandmarkModel lm = new LandmarkModel(root.resolve("landmark/face_landmarks_detector.onnx"))) {
            List<Detection> faces = det.detect(img, 0.6, 0.3);
            assertEquals(1, faces.size(), "expected one face");
            return lm.landmarks(img, faces.get(0));
        }
    }

    @Test
    void test_same_face_after_reencoding_is_bound_by_both_signals() throws Exception {
        Path root = modelsRoot();
        BufferedImage live = ImageIO.read(new File("../testdata/fixtures-faces/one_face.jpg"));
        BufferedImage stored = reencode(live);
        float[][] lmLive = landmarks(root, live);
        float[][] lmStored = landmarks(root, stored);
        try (SfaceEmbedder sface = new SfaceEmbedder(root.resolve("sface/face_recognition_sface_2021dec.onnx"))) {
            float[] eLive = sface.embed(FaceAlignment.align(live, lmLive));
            float[] eStored = sface.embed(FaceAlignment.align(stored, lmStored));
            Binding b = new Binding(0.363, 0.08, 2000, 3);
            b.observeLive(eLive, GeometryRatios.of(lmLive), 1.0);
            b.liveVerdictAt(0L);
            Binding.Result r = b.bind(1, eStored, GeometryRatios.of(lmStored), 1_000_000_000L, 5000);
            assertEquals(Binding.Verdict.BOUND, r.verdict(),
                    "failed " + r.failedChecks() + " cosine " + r.bestCosine() + " geometry diff " + r.geometryDifference());
            assertTrue(r.bestCosine() > 0.8, "cosine " + r.bestCosine());
        }
    }

    @Test
    void test_a_noise_image_does_not_match_the_face() throws Exception {
        Path root = modelsRoot();
        BufferedImage live = ImageIO.read(new File("../testdata/fixtures-faces/one_face.jpg"));
        float[][] lm = landmarks(root, live);
        Random rnd = new Random(1);
        BufferedImage noise = new BufferedImage(FaceAlignment.SIDE, FaceAlignment.SIDE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < FaceAlignment.SIDE; y++) {
            for (int x = 0; x < FaceAlignment.SIDE; x++) {
                noise.setRGB(x, y, rnd.nextInt(0xFFFFFF));
            }
        }
        try (SfaceEmbedder sface = new SfaceEmbedder(root.resolve("sface/face_recognition_sface_2021dec.onnx"))) {
            double cos = SfaceEmbedder.cosine(sface.embed(FaceAlignment.align(live, lm)), sface.embed(noise));
            assertTrue(cos < 0.363, "noise matched the face with cosine " + cos);
        }
    }

    @Test
    void test_binding_tolerates_a_small_alignment_error() throws Exception {
        // The spec warns that alignment errors cause false rejects. Move every landmark by 3 px, then by 2 px the other
        // way and rotate slightly: the same face must still match with room to spare above the 0.363 threshold.
        Path root = modelsRoot();
        BufferedImage live = ImageIO.read(new File("../testdata/fixtures-faces/one_face.jpg"));
        float[][] lm = landmarks(root, live);
        try (SfaceEmbedder sface = new SfaceEmbedder(root.resolve("sface/face_recognition_sface_2021dec.onnx"))) {
            float[] base = sface.embed(FaceAlignment.align(live, lm));
            double worstCosine = 1.0;
            double[][] shifts = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {-2, -2}};
            for (double[] sh : shifts) {
                float[][] moved = new float[lm.length][3];
                for (int i = 0; i < lm.length; i++) {
                    moved[i][0] = (float) (lm[i][0] + sh[0]);
                    moved[i][1] = (float) (lm[i][1] + sh[1]);
                    moved[i][2] = lm[i][2];
                }
                worstCosine = Math.min(worstCosine, SfaceEmbedder.cosine(base, sface.embed(FaceAlignment.align(live, moved))));
            }
            assertTrue(worstCosine > 0.7, "worst cosine under a 3 px landmark error: " + worstCosine);
        }
    }
}
