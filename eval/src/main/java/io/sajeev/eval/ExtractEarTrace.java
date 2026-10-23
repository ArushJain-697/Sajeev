// #61 (WS4) | Turns a clip of one blink (30 fps or more) into a "time_ms,ear" CSV for BlinkRecall. Java 11 only.
// Usage: ExtractEarTrace <models-dir> <clip.mp4> <out.csv> [fps]. Needs ffmpeg on the PATH.
package io.sajeev.eval;

import io.sajeev.engine.EngineModels;
import io.sajeev.face.Detection;
import io.sajeev.face.FaceFeatures;
import io.sajeev.image.AwtDecoder;
import io.sajeev.image.PixelImage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ExtractEarTrace {
    private ExtractEarTrace() {
    }

    public static void main(String[] a) throws Exception {
        int fps = a.length > 3 ? Integer.parseInt(a[3]) : 30;
        Path tmp = Files.createTempDirectory("sajeev-ear");
        Process p = new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "error", "-i", a[1], "-vf", "fps=" + fps, "-q:v", "3",
                tmp.resolve("%04d.jpg").toString()).inheritIO().start();
        if (p.waitFor() != 0) {
            throw new IOException("ffmpeg failed");
        }
        List<Path> frames;
        try (Stream<Path> s = Files.list(tmp)) {
            frames = s.sorted().collect(Collectors.toList());
        }
        List<String> out = new ArrayList<>();
        out.add("time_ms,ear");
        try (EngineModels m = new EngineModels(Paths.get(a[0]))) {
            for (int i = 0; i < frames.size(); i++) {
                PixelImage img = AwtDecoder.from(javax.imageio.ImageIO.read(frames.get(i).toFile()));
                List<Detection> faces = m.detector().detect(img, 0.6, 0.3);
                if (faces.size() != 1) {
                    continue; // a frame without exactly one face has no EAR
                }
                float[][] lm = m.landmarks().landmarks(img, faces.get(0));
                double ear = (FaceFeatures.eyeAspectRatio(lm, FaceFeatures.EYE_LEFT) + FaceFeatures.eyeAspectRatio(lm, FaceFeatures.EYE_RIGHT)) / 2.0;
                out.add(String.format(Locale.ROOT, "%.1f,%.4f", i * 1000.0 / fps, ear));
            }
        }
        Files.write(Paths.get(a[2]), out);
        System.out.println("wrote " + (out.size() - 1) + " samples to " + a[2]);
    }
}
