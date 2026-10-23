// #64 (WS4) | Per-stage timings on the emulator (an arm64 emulator on an Apple-silicon Mac: NOT a phone's speed).
package io.sajeev.engine.android;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import io.sajeev.engine.EngineModels;
import io.sajeev.engine.StageTimings;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import org.junit.Test;

public class StageTimingsOnEmulatorTest {
    @Test
    public void test_stage_timings_are_measured_on_the_emulator() throws Exception {
        Context t = InstrumentationRegistry.getInstrumentation().getContext();
        File models = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), "models");
        EngineModelsFiles.copy(t, models);
        AndroidImageDecoder.install();
        byte[] jpg;
        try (InputStream in = t.getAssets().open("one_face.jpg")) {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[65536];
            int r;
            while ((r = in.read(b)) > 0) {
                o.write(b, 0, r);
            }
            jpg = o.toByteArray();
        }
        try (EngineModels m = new EngineModels(models.toPath())) {
            Map<String, Double> r = StageTimings.measure(m, jpg, 10);
            double sum = 0;
            for (Map.Entry<String, Double> e : r.entrySet()) {
                android.util.Log.i("STAGES", String.format("| %s | %.1f |", e.getKey(), e.getValue()));
                sum += e.getValue();
            }
            android.util.Log.i("STAGES", String.format("| **total per processed frame** | **%.1f** |", sum));
            assertTrue(sum > 0);
        }
    }
}
