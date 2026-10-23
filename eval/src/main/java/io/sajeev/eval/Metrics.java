// #60 (WS4) | Bible §11 | Metrics per ISO/IEC 30107-3 style: APCER per species, max-APCER, BPCER, Wilson 95% intervals,
// per-subject and per-device breakdowns, baselines and passive-stage ablations. Output is deterministic JSON. Java 11 only.
package io.sajeev.eval;

import io.sajeev.api.Vote;
import io.sajeev.gate.DecisionGate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Pure computation over a list of presentations; the same input always gives the same bytes. */
public final class Metrics {

    private static final String[] VOTERS = {"V1_texture_minifasnet", "V2_moire", "V3_flatness", "V4_frame_edge", "V5_reflection"};

    private Metrics() {
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    /** A rate block: count, events, rate, Wilson interval. */
    private static String rate(int events, int n) {
        double[] w = Wilson.interval(events, n);
        return "{\"n\":" + n + ",\"events\":" + events + ",\"rate\":" + (n == 0 ? "null" : num((double) events / n))
                + ",\"wilson_low\":" + num(w[0]) + ",\"wilson_high\":" + num(w[1]) + "}";
    }

    /** Accept decision from the recorded votes only, using the gate with one voter left out (passive stage). */
    static boolean passiveAccept(Presentation p, String leaveOut, double threshold, int minLive) {
        List<Vote> votes = new ArrayList<>();
        for (String v : VOTERS) {
            if (v.equals(leaveOut) || !p.voteLabels.containsKey(v)) {
                continue;
            }
            Vote.Label l = Vote.Label.valueOf(p.voteLabels.get(v));
            double pr = l == Vote.Label.ABSTAIN ? 0.0 : p.voteProbabilities.get(v);
            votes.add(new Vote(l, pr, "eval"));
        }
        if (votes.isEmpty()) {
            return false;
        }
        double[] w = new double[votes.size()];
        java.util.Arrays.fill(w, 1.0 / votes.size());
        return DecisionGate.decide(votes, w, true, threshold, minLive, false).outcome() == DecisionGate.Outcome.PASSIVE_PASS;
    }

    private static int[] counts(List<Presentation> ps, java.util.function.Predicate<Presentation> acceptedFn, boolean bona) {
        int n = 0;
        int events = 0;
        for (Presentation p : ps) {
            if (p.isBonaFide() != bona) {
                continue;
            }
            n++;
            boolean acc = acceptedFn.test(p);
            if (bona ? !acc : acc) {
                events++; // BPCER event = a bona fide rejected; APCER event = an attack accepted
            }
        }
        return new int[] {events, n};
    }

    private static String block(List<Presentation> ps, java.util.function.Predicate<Presentation> acc) {
        TreeMap<String, String> species = new TreeMap<>();
        TreeSet<String> names = new TreeSet<>();
        for (Presentation p : ps) {
            if (!p.isBonaFide()) {
                names.add(p.species);
            }
        }
        double max = 0;
        int attackN = 0;
        int attackEv = 0;
        for (String s : names) {
            int n = 0;
            int ev = 0;
            for (Presentation p : ps) {
                if (s.equals(p.species)) {
                    n++;
                    if (acc.test(p)) {
                        ev++;
                    }
                }
            }
            species.put(s, rate(ev, n));
            if (n > 0) {
                max = Math.max(max, (double) ev / n);
            }
            attackN += n;
            attackEv += ev;
        }
        int[] b = counts(ps, acc, true);
        StringBuilder sb = new StringBuilder("{\"apcer_by_species\":{");
        boolean first = true;
        for (Map.Entry<String, String> e : species.entrySet()) {
            sb.append(first ? "" : ",").append('"').append(e.getKey()).append("\":").append(e.getValue());
            first = false;
        }
        sb.append("},\"max_apcer\":").append(names.isEmpty() ? "null" : num(max));
        sb.append(",\"apcer_all_attacks\":").append(rate(attackEv, attackN));
        sb.append(",\"bpcer\":").append(rate(b[0], b[1])).append('}');
        return sb.toString();
    }

    private static String group(List<Presentation> ps, java.util.function.Function<Presentation, String> key) {
        TreeSet<String> keys = new TreeSet<>();
        for (Presentation p : ps) {
            keys.add(key.apply(p));
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String k : keys) {
            List<Presentation> sub = new ArrayList<>();
            for (Presentation p : ps) {
                if (key.apply(p).equals(k)) {
                    sub.add(p);
                }
            }
            int[] a = counts(sub, p -> p.accepted, false);
            int[] b = counts(sub, p -> p.accepted, true);
            sb.append(first ? "" : ",").append('"').append(k).append("\":{\"apcer_pooled\":").append(rate(a[0], a[1]))
                    .append(",\"bpcer\":").append(rate(b[0], b[1])).append('}');
            first = false;
        }
        return sb.append('}').toString();
    }

    /**
     * @param ps all presentations (one per clip)
     * @param threshold passive threshold used for the ablation re-decisions
     * @param minLive LIVE votes needed for the ablation re-decisions
     * @return the metrics as deterministic JSON (sorted keys, fixed decimals, no times)
     */
    public static String toJson(List<Presentation> ps, double threshold, int minLive) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"unit\":\"one presentation (one clip); frames inside a clip are not counted separately\"");
        sb.append(",\"presentations\":").append(ps.size());
        sb.append(",\"engine\":").append(block(ps, p -> p.accepted));
        sb.append(",\"by_subject\":").append(group(ps, p -> p.subject));
        sb.append(",\"by_device\":").append(group(ps, p -> p.device));
        sb.append(",\"baselines\":{");
        sb.append("\"B0_no_liveness\":{\"note\":\"accepts everything, so APCER is 100% by construction\",\"result\":")
                .append(block(ps, p -> true)).append("}");
        sb.append(",\"B1_texture_cnn_alone\":{\"note\":\"accepts when V1 votes LIVE\",\"result\":")
                .append(block(ps, p -> "LIVE".equals(p.voteLabels.get(VOTERS[0])))).append("}");
        sb.append(",\"B2_blink_only\":{\"note\":\"not implemented: needs the challenge replayed per clip\"}");
        sb.append("}");
        sb.append(",\"ablations_passive_stage_only\":{\"note\":\"re-decided from the recorded votes with the gate; the active stage, strict mode, re-evaluation and Binding are not re-run here\"");
        sb.append(",\"all_voters\":").append(block(ps, p -> passiveAccept(p, null, threshold, minLive)));
        for (String v : VOTERS) {
            sb.append(",\"without_").append(v).append("\":").append(block(ps, p -> passiveAccept(p, v, threshold, minLive)));
        }
        sb.append("}");
        sb.append(",\"what_gets_through\":[");
        boolean first = true;
        List<Presentation> sorted = new ArrayList<>(ps);
        sorted.sort((a, b) -> a.file.compareTo(b.file));
        for (Presentation p : sorted) {
            if (!p.isBonaFide() && p.accepted) {
                sb.append(first ? "" : ",").append("{\"file\":\"").append(p.file).append("\",\"species\":\"").append(p.species)
                        .append("\",\"tier\":\"").append(p.tier).append("\",\"votes\":").append(p.voteLabels.toString().replace("=", ":")
                        .replaceAll("([A-Za-z0-9_]+):([A-Z]+)", "\"$1\":\"$2\"")).append("}");
                first = false;
            }
        }
        sb.append("]}");
        return sb.toString();
    }
}
