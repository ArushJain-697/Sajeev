// #39 (WS4) | Merges per-target result tables (made by InteropMain) and recorded-capture checks into one matrix. Java 11 only.
// Usage: MergeMain <out.md> <table.md>... [recorded:<name>=<capture.json>]...
package io.sajeev.interop;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MergeMain {

    private MergeMain() {
    }

    public static void main(String[] args) throws Exception {
        MatrixWriter m = new MatrixWriter();
        for (int i = 1; i < args.length; i++) {
            if (args[i].startsWith("recorded:")) {
                String[] nv = args[i].substring(9).split("=", 2);
                byte[] body = Files.readAllBytes(Paths.get(nv[1]));
                List<CheckResult> rs = new ArrayList<>();
                rs.add(SbiContractChecks.rCapture(body));
                rs.add(SbiContractChecks.digitalIdFields(body));
                m.add(nv[0] + " [rCapture only, recorded]", rs);
            } else {
                // a table written by InteropMain: header row names the target, then one row per check
                List<String> lines = Files.readAllLines(Paths.get(args[i]));
                String target = null;
                Map<String, String> cells = new LinkedHashMap<>();
                for (String l : lines) {
                    if (l.startsWith("| Check |")) {
                        target = l.substring("| Check |".length()).replace("|", "").trim();
                    } else if (l.startsWith("| ") && !l.startsWith("|---")) {
                        String[] p = l.split("\\|", -1);
                        if (p.length >= 3) {
                            cells.put(p[1].trim(), p[2].trim());
                        }
                    }
                }
                List<CheckResult> rs = new ArrayList<>();
                for (Map.Entry<String, String> e : cells.entrySet()) {
                    String c = e.getValue();
                    CheckResult.Status st = c.startsWith("PASS") ? CheckResult.Status.PASS
                            : c.startsWith("FAIL") ? CheckResult.Status.FAIL : CheckResult.Status.SKIP;
                    int colon = c.indexOf(": ");
                    rs.add(new CheckResult(e.getKey(), st, colon >= 0 ? c.substring(colon + 2) : c));
                }
                m.add(target, rs);
            }
        }
        Files.write(Paths.get(args[0]), m.markdown(LocalDate.now().toString()).getBytes("UTF-8"));
    }
}
