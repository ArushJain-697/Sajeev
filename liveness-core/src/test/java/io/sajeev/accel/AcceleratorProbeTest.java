// #10 (WS1) | Bible §6.x, DAY0 P0-8 | Mac part: does ORT offer CoreML here, and how far is its output from CPU?
package io.sajeev.accel;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class AcceleratorProbeTest {
    @Test
    void probe() throws Exception {
        String dir = System.getProperty("sajeev.models", System.getenv("SAJEEV_MODELS"));
        assumeTrue(dir != null, "set -Dsajeev.models");
        File m = new File(dir, "minifasnet/2.7_80x80_MiniFASNetV2.onnx");
        assumeTrue(m.exists(), "model missing");
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        float[] x = new float[3 * 80 * 80];
        for (int i = 0; i < x.length; i++) x[i] = ((i * 37) % 256) / 255f;
        float[] cpu = run(env, m, x, false);
        System.out.println("ACCEL cpu_first=" + cpu[0] + " " + cpu[1] + " " + cpu[2]);
        try {
            float[] ml = run(env, m, x, true);
            double worst = 0;
            for (int i = 0; i < cpu.length; i++) worst = Math.max(worst, Math.abs(cpu[i] - ml[i]));
            System.out.println("ACCEL coreml_max_abs_diff=" + worst);
        } catch (Exception e) {
            System.out.println("ACCEL coreml_unavailable=" + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static float[] run(OrtEnvironment env, File m, float[] x, boolean coreml) throws Exception {
        try (OrtSession.SessionOptions o = new OrtSession.SessionOptions()) {
            if (coreml) o.addCoreML();
            try (OrtSession s = env.createSession(m.getPath(), o);
                 OnnxTensor in = OnnxTensor.createTensor(env, FloatBuffer.wrap(x), new long[] {1, 3, 80, 80});
                 OrtSession.Result r = s.run(Collections.singletonMap(s.getInputNames().iterator().next(), in))) {
                float[][] v = (float[][]) r.get(0).getValue();
                return v[0];
            }
        }
    }
}
