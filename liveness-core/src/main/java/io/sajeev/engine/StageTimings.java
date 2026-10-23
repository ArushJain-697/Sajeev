// #64 (WS4) | Bible §11 | Per-stage cost of one frame: decode, face detector, landmark mesh, texture CNN, moire FFT, flatness
// (homography), frame edge, reflection, embedding. Same code runs on the Mac and on Android. Java 11 only.
package io.sajeev.engine;

import io.sajeev.api.Frame;
import io.sajeev.api.Vote;
import io.sajeev.binding.FaceAlignment;
import io.sajeev.face.Detection;
import io.sajeev.image.ImageCodec;
import io.sajeev.image.PixelImage;
import io.sajeev.passive.edge.FrameEdgeVoter;
import io.sajeev.passive.flatness.FlatnessVoter;
import io.sajeev.passive.moire.MoireVoter;
import io.sajeev.passive.reflection.ReflectionVoter;
import io.sajeev.passive.texture.TextureCnnVoter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Measures the median milliseconds of each stage over several runs, after a warm-up. */
public final class StageTimings {

    private StageTimings() {
    }

    private static double median(List<Double> v) {
        List<Double> s = new ArrayList<>(v);
        Collections.sort(s);
        return s.get(s.size() / 2);
    }

    /**
     * @param models loaded models
     * @param jpeg a JPEG with exactly one face
     * @param runs measured runs per stage (after 3 warm-up runs)
     * @return stage name to median milliseconds, in pipeline order
     * @throws Exception if a stage fails
     */
    public static Map<String, Double> measure(EngineModels models, byte[] jpeg, int runs) throws Exception {
        Map<String, List<Double>> t = new LinkedHashMap<>();
        for (String k : new String[] {"decode", "face detector (YuNet)", "landmark mesh", "texture CNN (2 models)", "moire FFT (3 frames)",
            "flatness homography", "frame edge", "reflection", "embedding + alignment"}) {
            t.put(k, new ArrayList<Double>());
        }
        PixelImage img0 = ImageCodec.decode(jpeg);
        Detection face0 = models.detector.detect(img0, 0.6, 0.3).get(0);
        float[][] lm0 = models.landmarks.landmarks(img0, face0);
        // a second landmark set with a turned head, so the homography has real work to do
        float[][] lm1 = new float[lm0.length][];
        for (int i = 0; i < lm0.length; i++) {
            lm1[i] = new float[] {lm0[i][0] + (float) (0.12 * (lm0[i][2] - lm0[1][2])), lm0[i][1], lm0[i][2]};
        }
        TextureCnnVoter tex = new TextureCnnVoter(models.minifasV2, models.minifasV1se, models.detector, 0.8, 0.2);
        MoireVoter moire = new MoireVoter(models.detector, 0.2);
        FlatnessVoter flat = new FlatnessVoter(0.14, 0.02, 200, 0.03, 42L);
        FrameEdgeVoter edge = new FrameEdgeVoter(0.5, 0.8, 12);
        ReflectionVoter refl = new ReflectionVoter(0.02, 0.6, 0.15, 0.95);
        Frame f = new Frame(jpeg, 0, 0);
        List<Frame> window = new ArrayList<>(Collections.nCopies(3, f));
        for (int i = 0; i < runs + 3; i++) {
            boolean keep = i >= 3;
            long a = System.nanoTime();
            PixelImage img = ImageCodec.decode(jpeg);
            long b = System.nanoTime();
            Detection face = models.detector.detect(img, 0.6, 0.3).get(0);
            long c = System.nanoTime();
            float[][] lm = models.landmarks.landmarks(img, face);
            long d = System.nanoTime();
            Vote v1 = tex.vote(Collections.singletonList(f));
            long e = System.nanoTime();
            Vote v2 = moire.vote(window);
            long g = System.nanoTime();
            Vote v3 = flat.vote(java.util.Arrays.asList(lm0, lm1));
            long h = System.nanoTime();
            Vote v4 = edge.vote(img, face);
            long k = System.nanoTime();
            Vote v5 = refl.vote(img, face);
            long l = System.nanoTime();
            models.sface.embed(FaceAlignment.align(img, lm));
            long m = System.nanoTime();
            if (keep && v1 != null && v2 != null && v3 != null && v4 != null && v5 != null) {
                t.get("decode").add((b - a) / 1e6);
                t.get("face detector (YuNet)").add((c - b) / 1e6);
                t.get("landmark mesh").add((d - c) / 1e6);
                t.get("texture CNN (2 models)").add((e - d) / 1e6 - (c - b) / 1e6 - (b - a) / 1e6); // vote() decodes and detects itself
                t.get("moire FFT (3 frames)").add((g - e) / 1e6 - 3 * ((c - b) / 1e6 + (b - a) / 1e6));
                t.get("flatness homography").add((h - g) / 1e6);
                t.get("frame edge").add((k - h) / 1e6);
                t.get("reflection").add((l - k) / 1e6);
                t.get("embedding + alignment").add((m - l) / 1e6);
            }
        }
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<Double>> e : t.entrySet()) {
            out.put(e.getKey(), Math.max(0.0, median(e.getValue())));
        }
        return out;
    }
}
