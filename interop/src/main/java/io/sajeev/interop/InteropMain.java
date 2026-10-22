// #39 (WS4) | Runs the contract checks and writes the matrix. Java 11 only.
// Usage: InteropMain <out.md> live:<name>=<host>:<port>[:<streamSeconds>] ... recorded:<name>=<capture.json> ...
package io.sajeev.interop;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class InteropMain {

    private InteropMain() {
    }

    public static void main(String[] args) throws Exception {
        MatrixWriter m = new MatrixWriter();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("live:")) {
                String[] nv = a.substring(5).split("=", 2);
                String[] hp = nv[1].split(":");
                int secs = hp.length > 2 ? Integer.parseInt(hp[2]) : 6;
                m.add(nv[0], SbiContractChecks.runLive(hp[0], Integer.parseInt(hp[1]), secs));
            } else if (a.startsWith("recorded:")) {
                String[] nv = a.substring(9).split("=", 2);
                byte[] body = Files.readAllBytes(Paths.get(nv[1]));
                List<CheckResult> rs = new ArrayList<>();
                rs.add(SbiContractChecks.rCapture(body));
                rs.add(SbiContractChecks.digitalIdFields(body));
                m.add(nv[0] + " (recorded rCapture only)", rs);
            }
        }
        Files.write(Paths.get(args[0]), m.markdown(LocalDate.now().toString()).getBytes("UTF-8"));
        System.out.println("wrote " + args[0]);
    }
}
