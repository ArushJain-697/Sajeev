// #63 (WS4) | The held-out report as Markdown, generated from the presentations. Limits come first. Java 11 only.
package io.sajeev.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.TreeSet;

/** Builds the report text; the numbers are computed here from the same presentations as metrics.json. */
public final class HeldOutReport {

    private HeldOutReport() {
    }

    private static String pct(int k, int n) {
        double[] w = Wilson.interval(k, n);
        return String.format(Locale.ROOT, "%d / %d = %.1f%% (95%% Wilson %.1f%% to %.1f%%)", k, n, n == 0 ? 0.0 : 100.0 * k / n,
                100 * w[0], 100 * w[1]);
    }

    /**
     * @param ps the held-out presentations
     * @param configHash the freeze hash the run was checked against
     * @return the Markdown report
     */
    public static String markdown(List<Presentation> ps, String configHash) {
        StringBuilder b = new StringBuilder("# Held-out report\n\n## Limits (read first)\n\n");
        int bona = 0;
        int attacks = 0;
        for (Presentation p : ps) {
            if (p.isBonaFide()) {
                bona++;
            } else {
                attacks++;
            }
        }
        b.append("- ").append(ps.size()).append(" presentations: ").append(bona).append(" bona fide, ").append(attacks)
                .append(" attacks. The unit is one clip; frames inside a clip are not counted separately.\n");
        b.append("- With this few presentations the intervals are wide; a rate of 0% only means no failure was seen. It does not mean the engine cannot fail.\n");
        b.append("- Thresholds were tuned on the DEV recordings only and frozen before this run (freeze hash `").append(configHash)
                .append("`). The held-out files were sealed by hash before tuning; the access log shows the openings.\n");
        b.append("- The result is for these subjects, devices, prints and screens only. It is not a lab evaluation and not a certification.\n");
        b.append("- The final decision includes the active stage only as far as the clip reached it; Binding against a face swap (P4) needs the capture assembled by the recording protocol and is reported only if species P4 clips exist.\n\n");
        b.append("## Error rates by attack species (attacks accepted)\n\n| Species | Accepted |\n|---|---|\n");
        TreeSet<String> sp = new TreeSet<>();
        for (Presentation p : ps) {
            if (!p.isBonaFide()) {
                sp.add(p.species);
            }
        }
        for (String s : sp) {
            int n = 0;
            int k = 0;
            for (Presentation p : ps) {
                if (s.equals(p.species)) {
                    n++;
                    if (p.accepted) {
                        k++;
                    }
                }
            }
            b.append("| ").append(s).append(" | ").append(pct(k, n)).append(" |\n");
        }
        int rej = 0;
        for (Presentation p : ps) {
            if (p.isBonaFide() && !p.accepted) {
                rej++;
            }
        }
        b.append("\n## Bona fide presentations rejected (BPCER)\n\n").append(pct(rej, bona)).append("\n");
        for (String[] g : new String[][] {{"device", "d"}, {"subject", "s"}}) {
            TreeMap<String, int[]> m = new TreeMap<>();
            for (Presentation p : ps) {
                String key = g[1].equals("d") ? p.device : p.subject;
                int[] c = m.computeIfAbsent(key, k -> new int[4]);
                if (p.isBonaFide()) {
                    c[1]++;
                    if (!p.accepted) {
                        c[0]++;
                    }
                } else {
                    c[3]++;
                    if (p.accepted) {
                        c[2]++;
                    }
                }
            }
            b.append("\n## By ").append(g[0]).append("\n\n| ").append(g[0]).append(" | Attacks accepted | Bona fide rejected |\n|---|---|---|\n");
            for (java.util.Map.Entry<String, int[]> e : m.entrySet()) {
                b.append("| ").append(e.getKey()).append(" | ").append(pct(e.getValue()[2], e.getValue()[3])).append(" | ")
                        .append(pct(e.getValue()[0], e.getValue()[1])).append(" |\n");
            }
        }
        b.append("\n## What gets through (accepted attacks)\n\n");
        List<Presentation> through = new ArrayList<>();
        for (Presentation p : ps) {
            if (!p.isBonaFide() && p.accepted) {
                through.add(p);
            }
        }
        through.sort((x, y) -> x.file.compareTo(y.file));
        if (through.isEmpty()) {
            b.append("No attack was accepted in this set (see the limits above).\n");
        } else {
            b.append("| File | Species | Tier | Votes |\n|---|---|---|---|\n");
            for (Presentation p : through) {
                b.append("| ").append(p.file).append(" | ").append(p.species).append(" | ").append(p.tier).append(" | ")
                        .append(p.voteLabels).append(" |\n");
            }
        }
        return b.toString();
    }
}
