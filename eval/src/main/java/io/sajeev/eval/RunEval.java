// #60 (WS4) | Bible §11 | run_eval: run the real engine on every clip of a manifest and write metrics JSON. Java 11 only.
// Usage: run_eval --manifest <manifest.csv> --clips <dir> --models <dir> --out <metrics.json> [--threshold 0.80] [--min-live 3]
// A clip is an .mp4 (frames taken with ffmpeg at the manifest fps) or a folder of .jpg frames. One clip = one presentation.
package io.sajeev.eval;

import io.sajeev.api.Frame;
import io.sajeev.api.LivenessSession;
import io.sajeev.api.PolicyConfig;
import io.sajeev.api.Verdict;
import io.sajeev.api.Vote;
import io.sajeev.api.Workflow;
import io.sajeev.audit.AuditLog;
import io.sajeev.engine.EngineModels;
import io.sajeev.engine.EngineSession;
import io.sajeev.engine.EngineSettings;
import io.sajeev.engine.LivenessEngine;
import io.sajeev.metrics.LivenessMetrics;
import io.sajeev.policy.Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Command-line evaluation. */
public final class RunEval {

    private RunEval() {
    }

    public static void main(String[] args) throws Exception {
        String manifest = null;
        String clips = null;
        String models = System.getProperty("sajeev.models", System.getenv("SAJEEV_MODELS"));
        String out = "metrics.json";
        double threshold = 0.80;
        int minLive = 3;
        boolean heldout = false;
        String rerun = null;
        String evalDir = "eval";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--manifest": manifest = args[++i]; break;
                case "--clips": clips = args[++i]; break;
                case "--models": models = args[++i]; break;
                case "--out": out = args[++i]; break;
                case "--threshold": threshold = Double.parseDouble(args[++i]); break;
                case "--min-live": minLive = Integer.parseInt(args[++i]); break;
                case "--heldout": heldout = true; break;
                case "--rerun": rerun = args[++i]; break;
                case "--eval-dir": evalDir = args[++i]; break;
                default:
                    System.err.println("unknown option " + args[i]);
                    System.exit(2);
            }
        }
        String configHash = null;
        if (heldout) {
            // the held-out set is evaluated only through the gate (HELDOUT_PROTOCOL section 4)
            try {
                configHash = FreezeHash.compute(Paths.get(models), commit());
                HeldOutGate.check(Paths.get(evalDir), configHash, rerun);
            } catch (HeldOutGate.Refused r) {
                System.err.println("REFUSED: " + r.getMessage());
                System.exit(4);
            }
            manifest = Paths.get(evalDir, "data", "heldout", "manifest.csv").toString();
            clips = Paths.get(evalDir, "data", "heldout").toString();
            out = Paths.get(evalDir, "metrics_heldout.json").toString();
        }
        if (manifest == null || clips == null || models == null) {
            System.err.println("usage: run_eval --manifest <csv> --clips <dir> --models <dir> --out <json> [--threshold N] [--min-live N]");
            System.exit(2);
        }
        List<Presentation> ps = new ArrayList<>();
        try (LivenessEngine engine = new LivenessEngine(new EngineModels(Paths.get(models)), Config.load(new Properties()),
                EngineSettings.defaults(), new AuditLog(System::currentTimeMillis), new LivenessMetrics())) {
            for (String[] row : readManifest(Paths.get(manifest))) {
                ps.add(run(engine, Paths.get(clips), row));
            }
        }
        Files.write(Paths.get(out), Metrics.toJson(ps, threshold, minLive).getBytes(StandardCharsets.UTF_8));
        if (heldout) {
            Files.write(Paths.get(evalDir, "HELDOUT_REPORT.md"), HeldOutReport.markdown(ps, configHash).getBytes(StandardCharsets.UTF_8));
        }
        System.out.println("wrote " + out + " for " + ps.size() + " presentations");
    }

    private static String commit() {
        try {
            Process p = new ProcessBuilder("git", "rev-parse", "HEAD").redirectErrorStream(true).start();
            String h = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return p.waitFor() == 0 ? h : "unknown";
        } catch (IOException | InterruptedException e) {
            return "unknown";
        }
    }

    /** @return the rows (file, species, subject, device, fps) of a manifest.csv written by eval/ingest.sh */
    static List<String[]> readManifest(Path manifest) throws IOException {
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        List<String[]> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] c = lines.get(i).split(",");
            if (c.length >= 8) {
                rows.add(new String[] {c[0], c[5], c[3], c[4], c[6]});
            }
        }
        rows.sort(Comparator.comparing(r -> r[0]));
        return rows;
    }

    static double fps(String s) {
        if (s.contains("/")) {
            String[] p = s.split("/");
            return Double.parseDouble(p[0]) / Double.parseDouble(p[1]);
        }
        return Double.parseDouble(s);
    }

    private static List<Path> frames(Path clipsRoot, String file, double fps) throws IOException, InterruptedException {
        Path p = clipsRoot.resolve(file);
        if (Files.isDirectory(p)) {
            try (Stream<Path> s = Files.list(p)) {
                return s.filter(f -> f.toString().endsWith(".jpg")).sorted().collect(Collectors.toList());
            }
        }
        Path tmp = Files.createTempDirectory("sajeev-eval");
        Process pr = new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "error", "-i", p.toString(), "-q:v", "3",
                tmp.resolve("%04d.jpg").toString()).inheritIO().start();
        if (pr.waitFor() != 0) {
            throw new IOException("ffmpeg failed for " + p);
        }
        try (Stream<Path> s = Files.list(tmp)) {
            return s.sorted().collect(Collectors.toList());
        }
    }

    static Presentation run(LivenessEngine engine, Path clipsRoot, String[] row) throws Exception {
        double fps = fps(row[4]);
        long step = (long) (1_000_000_000.0 / fps);
        EngineSession s = engine.start(Workflow.RESIDENT, new PolicyConfig(0.80, 2, 3));
        List<Path> frames = frames(clipsRoot, row[0], fps);
        long t0 = 1_000_000_000L;
        for (int i = 0; i < frames.size() && s.state() != LivenessSession.State.DONE
                && s.state() != LivenessSession.State.ABORTED; i++) {
            s.onFrame(new Frame(Files.readAllBytes(frames.get(i)), t0 + i * step, i));
            Thread.sleep(15);
        }
        long end = System.currentTimeMillis() + 8000;
        while (s.verdict() == null && System.currentTimeMillis() < end) {
            Thread.sleep(50);
        }
        Verdict v = s.verdict();
        boolean accepted = v != null && (v.tier() == Verdict.Tier.LIVE_PASSIVE || v.tier() == Verdict.Tier.LIVE_ACTIVE);
        Map<String, String> labels = new TreeMap<>();
        Map<String, Double> probs = new TreeMap<>();
        for (Map.Entry<String, Vote> e : s.passiveVotes().entrySet()) {
            labels.put(e.getKey(), e.getValue().label().name());
            probs.put(e.getKey(), e.getValue().probability());
        }
        s.abort();
        return new Presentation(row[0], row[1], row[2], row[3], accepted, v == null ? "NO_DECISION" : v.tier().name(),
                labels, probs, false);
    }
}
