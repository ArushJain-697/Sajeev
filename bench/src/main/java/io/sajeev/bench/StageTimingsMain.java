// #64 (WS4) | Prints the per-stage table in Markdown. Usage: StageTimingsMain <models-dir> <face.jpg> [runs]
package io.sajeev.bench;

import io.sajeev.engine.EngineModels;
import io.sajeev.engine.StageTimings;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public final class StageTimingsMain {
    private StageTimingsMain() {
    }

    public static void main(String[] a) throws Exception {
        try (EngineModels m = new EngineModels(Paths.get(a[0]))) {
            Map<String, Double> r = StageTimings.measure(m, Files.readAllBytes(Paths.get(a[1])), a.length > 2 ? Integer.parseInt(a[2]) : 20);
            double sum = 0;
            for (Map.Entry<String, Double> e : r.entrySet()) {
                System.out.printf("| %s | %.1f |%n", e.getKey(), e.getValue());
                sum += e.getValue();
            }
            System.out.printf("| **total per processed frame** | **%.1f** |%n", sum);
        }
    }
}
