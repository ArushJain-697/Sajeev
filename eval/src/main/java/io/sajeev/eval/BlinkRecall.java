// #61 (WS4) | Blink detector recall as a function of frame rate. Java 11 only.
// A blink is an eye-aspect-ratio (EAR) dip. A blink trace (EAR over time at a high rate) is resampled to 3, 5, 8, 10, 15, 30 fps
// at several sampling phases, the real BLINK detector runs on each, and recall is the share of traces where it fired.
// Traces: SYNTHETIC (a raised-cosine dip of 100 to 400 ms) or real ones read from CSV (time_ms,ear) once recordings exist.
package io.sajeev.eval;

import io.sajeev.active.ActionDetectors;
import io.sajeev.active.ActionThresholds;
import io.sajeev.active.FrameFeatures;
import io.sajeev.api.ChallengeType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The experiment and the rule that picks the minimum fps. */
public final class BlinkRecall {

    /** The frame rates of the experiment (ticket #61). */
    public static final int[] FPS = {3, 5, 8, 10, 15, 30};
    /** The rule: the lowest fps whose recall is at least this share, and stays above it for every higher fps. */
    public static final double REQUIRED_RECALL = 0.95;

    /** One high-rate blink trace: EAR sampled every {@code stepMs}. */
    public static final class Trace {
        final double[] ear;
        final double stepMs;
        final String label;

        Trace(double[] ear, double stepMs, String label) {
            this.ear = ear;
            this.stepMs = stepMs;
            this.label = label;
        }
    }

    private BlinkRecall() {
    }

    /** @return SYNTHETIC blinks: 1.2 s of open eyes (EAR 0.30), a raised-cosine dip to 0.05 lasting {@code durationMs}, 0.8 s open */
    public static Trace synthetic(int durationMs) {
        double step = 1000.0 / 120; // 120 samples per second
        int n = (int) (2000 / step);
        double[] ear = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i * step;
            double start = 1200;
            if (t >= start && t < start + durationMs) {
                double x = (t - start) / durationMs; // 0..1
                ear[i] = 0.30 - 0.25 * (0.5 - 0.5 * Math.cos(2 * Math.PI * x));
            } else {
                ear[i] = 0.30;
            }
        }
        return new Trace(ear, step, "synthetic_" + durationMs + "ms");
    }

    /** @param csv lines "time_ms,ear" at a constant rate, one blink per file @return the trace */
    public static Trace fromCsv(Path csv) throws IOException {
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        List<Double> t = new ArrayList<>();
        List<Double> e = new ArrayList<>();
        for (String l : lines) {
            if (l.trim().isEmpty() || l.startsWith("time")) {
                continue;
            }
            String[] c = l.split(",");
            t.add(Double.parseDouble(c[0]));
            e.add(Double.parseDouble(c[1]));
        }
        double[] ear = new double[e.size()];
        for (int i = 0; i < ear.length; i++) {
            ear[i] = e.get(i);
        }
        double step = (t.get(t.size() - 1) - t.get(0)) / (t.size() - 1);
        return new Trace(ear, step, csv.getFileName().toString());
    }

    /** @return true if the BLINK detector fires when the trace is sampled at {@code fps} starting at {@code phaseMs} */
    static boolean detects(Trace tr, int fps, double phaseMs) {
        double periodMs = 1000.0 / fps;
        List<FrameFeatures> first = new ArrayList<>();
        long seq = 0;
        double total = (tr.ear.length - 1) * tr.stepMs;
        List<FrameFeatures> frames = new ArrayList<>();
        for (double t = phaseMs; t <= total; t += periodMs) {
            double ear = tr.ear[(int) Math.min(tr.ear.length - 1, Math.round(t / tr.stepMs))];
            frames.add(new FrameFeatures(seq++, (long) (t * 1_000_000), 0, 0, ear, 0, 0, 0, 0));
        }
        for (FrameFeatures f : frames) {
            if (f.arrivalNanos() < 1_000_000_000L) {
                first.add(f); // the first second, before the blink, gives the baseline
            }
        }
        if (first.isEmpty()) {
            return false;
        }
        ActionDetectors.Detector d = ActionDetectors.create(ChallengeType.BLINK, ActionDetectors.Baseline.of(first), ActionThresholds.defaults());
        boolean hit = false;
        for (FrameFeatures f : frames) {
            if (f.arrivalNanos() >= 1_000_000_000L) {
                hit |= d.update(f);
            }
        }
        return hit;
    }

    /** @return recall for each fps in {@link #FPS}, over all traces and 10 evenly spread sampling phases */
    public static double[] recall(List<Trace> traces) {
        double[] r = new double[FPS.length];
        for (int k = 0; k < FPS.length; k++) {
            int hit = 0;
            int total = 0;
            for (Trace tr : traces) {
                for (int ph = 0; ph < 10; ph++) {
                    total++;
                    if (detects(tr, FPS[k], ph * (1000.0 / FPS[k]) / 10)) {
                        hit++;
                    }
                }
            }
            r[k] = (double) hit / total;
        }
        return r;
    }

    /** @return the chosen minimum fps by the rule, or -1 if no tested rate reaches the required recall */
    public static int choose(double[] recall) {
        int chosen = -1;
        for (int k = FPS.length - 1; k >= 0; k--) {
            if (recall[k] >= REQUIRED_RECALL) {
                chosen = FPS[k];
            } else {
                break;
            }
        }
        return chosen;
    }

    /** @return a Markdown table (and an ASCII bar per row, standing in for the chart) */
    public static String table(double[] recall, String source) {
        StringBuilder b = new StringBuilder("| fps | blink recall | |\n|---|---|---|\n");
        for (int k = 0; k < FPS.length; k++) {
            int bars = (int) Math.round(recall[k] * 30);
            b.append(String.format(Locale.ROOT, "| %d | %.1f%% | %s |%n", FPS[k], 100 * recall[k], "#".repeat(bars)));
        }
        return b.append("\nSource of the traces: ").append(source).append("\n").toString();
    }

    /** Usage: BlinkRecall [csv-dir]; without a folder the synthetic traces are used. Prints the table and the chosen value. */
    public static void main(String[] a) throws IOException {
        List<Trace> traces = new ArrayList<>();
        String source;
        if (a.length > 0) {
            try (java.util.stream.Stream<Path> s = Files.list(java.nio.file.Paths.get(a[0]))) {
                for (Path p : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".csv")).sorted()::iterator) {
                    traces.add(fromCsv(p));
                }
            }
            source = "REAL traces from " + a[0] + " (" + traces.size() + " blinks)";
        } else {
            for (int d : new int[] {100, 150, 200, 250, 300, 400}) {
                traces.add(synthetic(d));
            }
            source = "SYNTHETIC blinks of 100, 150, 200, 250, 300 and 400 ms (raised-cosine EAR dip). NOT real people: re-run with real traces before trusting the chosen value.";
        }
        double[] r = recall(traces);
        System.out.print(table(r, source));
        System.out.println("CHOSEN_BLINK_MIN_FPS=" + choose(r));
    }
}
