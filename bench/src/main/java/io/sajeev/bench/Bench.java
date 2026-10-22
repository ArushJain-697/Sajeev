// #47 (WS2) | Bible §6 | Liveness Bench, minimal (G1): run the REAL engine on a clip and print what it decided. Java 11 only.
// Every output starts with the label BENCH · MOCK DEVICE · NO MOSIP SERVER. The clip is a folder of .jpg frames, one .jpg
// (repeated), or a recorded M-JPEG stream. Frame arrival times are synthetic (index / fps), so a run is repeatable.
package io.sajeev.bench;

import io.sajeev.api.DeviceException;
import io.sajeev.api.Frame;
import io.sajeev.api.FrameSource;
import io.sajeev.api.LivenessSession;
import io.sajeev.api.PolicyConfig;
import io.sajeev.api.Verdict;
import io.sajeev.api.Vote;
import io.sajeev.api.Workflow;
import io.sajeev.audit.AuditLog;
import io.sajeev.binding.Binding;
import io.sajeev.devices.FileFrameSource;
import io.sajeev.engine.EngineModels;
import io.sajeev.engine.EngineSession;
import io.sajeev.engine.EngineSettings;
import io.sajeev.engine.LivenessEngine;
import io.sajeev.frame.MjpegParser;
import io.sajeev.metrics.LivenessMetrics;
import io.sajeev.policy.Config;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** Command-line bench. */
public final class Bench {

    /** The label that every Bench screen and printout carries (PDF IM3: never mistaken for a real registration). */
    public static final String LABEL = "BENCH · MOCK DEVICE · NO MOSIP SERVER";

    private Bench() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * @param args --clip <path> [--fps N] [--models <dir>] [--workflow RESIDENT|OPERATOR|SUPERVISOR] [--capture <image>] [--frames N]
     * @param out where the report goes
     * @param err where usage errors go
     * @return 0 when a verdict was produced, 2 for usage errors, 3 when no verdict came
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        String clip = null;
        String models = System.getProperty("sajeev.models", System.getenv("SAJEEV_MODELS"));
        String capture = null;
        double fps = 10.0;
        int still = 40;
        Workflow wf = Workflow.RESIDENT;
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--clip": clip = args[++i]; break;
                    case "--fps": fps = Double.parseDouble(args[++i]); break;
                    case "--models": models = args[++i]; break;
                    case "--capture": capture = args[++i]; break;
                    case "--frames": still = Integer.parseInt(args[++i]); break;
                    case "--workflow": wf = Workflow.valueOf(args[++i].toUpperCase()); break;
                    default: throw new IllegalArgumentException("unknown option " + args[i]);
                }
            }
        } catch (RuntimeException e) {
            err.println("usage: run.sh --clip <folder|file.jpg|file.mjpeg> [--fps N] [--models <dir>] [--workflow RESIDENT|OPERATOR|SUPERVISOR] [--capture <image>] [--frames N]");
            err.println("error: " + e.getMessage());
            return 2;
        }
        if (clip == null || models == null) {
            err.println("need --clip and a model directory (--models or SAJEEV_MODELS)");
            return 2;
        }
        out.println(LABEL);
        List<byte[]> jpegs;
        try {
            jpegs = load(Paths.get(clip), still);
        } catch (IOException | DeviceException e) {
            err.println("cannot read the clip: " + e.getMessage());
            return 2;
        }
        long step = (long) (1_000_000_000.0 / fps);
        try (LivenessEngine engine = new LivenessEngine(new EngineModels(Paths.get(models)), Config.load(new Properties()),
                EngineSettings.defaults(), new AuditLog(System::currentTimeMillis), new LivenessMetrics())) {
            EngineSession s = engine.start(wf, new PolicyConfig(0.80, 2, 3));
            int fed = 0;
            long t0 = 1_000_000_000L;
            for (byte[] j : jpegs) {
                if (s.state() == LivenessSession.State.DONE || s.state() == LivenessSession.State.ABORTED) {
                    break;
                }
                s.onFrame(new Frame(j, t0 + fed * step, fed));
                fed++;
                Thread.sleep(15);
            }
            long end = System.currentTimeMillis() + 8_000; // the clip is over; give the worker time to drain
            while (s.verdict() == null && System.currentTimeMillis() < end) {
                Thread.sleep(50);
            }
            out.println("clip: " + clip + " (" + jpegs.size() + " frames available, " + fed + " fed)");
            out.printf("measured fps: %.2f (replay rate of the clip, synthetic frame times)%n", fps);
            Verdict v = s.verdict();
            if (v == null) {
                out.println("verdict: NONE (the clip ended before the engine decided; a CANNOT_JUDGE needs 10 s of frames with no usable face)");
                return 3;
            }
            out.println("verdict: " + v.tier() + (v.error().isPresent() ? " error=" + v.error().get() : "") + " reason=" + v.reason());
            for (Map.Entry<String, Vote> e : s.passiveVotes().entrySet()) {
                out.printf("vote %-22s %-8s p=%.3f %s%n", e.getKey(), e.getValue().label(), e.getValue().probability(),
                        e.getValue().reason());
            }
            if (capture != null) {
                Binding.Result r = s.checkCapture(Files.readAllBytes(Paths.get(capture)), System.nanoTime(), 3000);
                out.println("binding: " + r.verdict() + " failed=" + r.failedChecks());
            }
            out.println("policy result: " + s.attemptResult());
            return 0;
        } catch (Exception e) {
            err.println("bench failed: " + e);
            return 3;
        }
    }

    private static List<byte[]> load(Path clip, int stillRepeat) throws IOException, DeviceException {
        List<byte[]> out = new ArrayList<>();
        String name = clip.getFileName().toString().toLowerCase();
        if (Files.isDirectory(clip)) {
            try (FileFrameSource src = new FileFrameSource(clip, 10)) {
                Optional<Frame> f;
                while ((f = src.next(0)).isPresent()) {
                    out.add(f.get().jpeg());
                }
            }
        } else if (name.endsWith(".mjpeg") || name.endsWith(".mjpg")) {
            byte[] all = Files.readAllBytes(clip);
            String head = new String(all, 0, Math.min(all.length, 200), StandardCharsets.ISO_8859_1);
            String boundary = head.startsWith("--") ? head.substring(2, head.indexOf('\r') > 0 ? head.indexOf('\r') : head.indexOf('\n')) : "BoundaryString";
            try (InputStream in = new BufferedInputStream(Files.newInputStream(clip))) {
                MjpegParser p = new MjpegParser(in, boundary);
                byte[] jpeg;
                while ((jpeg = p.next()) != null) {
                    out.add(jpeg);
                }
            }
        } else {
            byte[] one = Files.readAllBytes(clip);
            for (int i = 0; i < stillRepeat; i++) {
                out.add(one);
            }
        }
        if (out.isEmpty()) {
            throw new IOException("no frames in " + clip);
        }
        return out;
    }
}
