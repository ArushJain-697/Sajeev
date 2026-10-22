// SAJEEV #10 test probe (test tooling only). Runs MiniFASNetV2 with ONNX Runtime Android on CPU and on NNAPI,
// compares both with the PyTorch reference logits and logs latency. Reference input: ((i*37)%256)/255, CHW 1x3x80x80.
package io.sajeev.accelprobe;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Collections;

public class MainActivity extends Activity {
    private static final String TAG = "ACCEL";
    // PyTorch reference logits for the reference input (testdata/reference/minifas_pytorch_ref.json)
    private static final float[] REF = {-3.4633384f, -0.6663624f, 4.1305933f};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        new Thread(this::run).start();
    }

    private void run() {
        try {
            // The ONNX export keeps its weights in a ".data" sidecar, so both files go to the app's files dir
            // and the session is created from the path (bytes alone cannot find the sidecar).
            String path = copyAsset("2.7_80x80_MiniFASNetV2.onnx");
            copyAsset("2.7_80x80_MiniFASNetV2.onnx.data");
            float[] x = new float[3 * 80 * 80];
            for (int i = 0; i < x.length; i++) {
                x[i] = ((i * 37) % 256) / 255f;
            }
            Log.i(TAG, "device abi=" + Build.SUPPORTED_ABIS[0] + " api=" + Build.VERSION.SDK_INT + " model=" + Build.MODEL);
            Result cpu = infer(path, x, false);
            Log.i(TAG, "CPU ok maxAbsErrVsPyTorch=" + cpu.err + " avgMs=" + cpu.ms);
            try {
                Result nn = infer(path, x, true);
                Log.i(TAG, "NNAPI ok maxAbsErrVsPyTorch=" + nn.err + " maxAbsDiffVsCpu=" + nn.diffVsCpu(cpu) + " avgMs=" + nn.ms);
            } catch (Throwable t) {
                Log.i(TAG, "NNAPI unavailable: " + t);
            }
        } catch (Throwable t) {
            Log.i(TAG, "FAILED " + t);
        }
        Log.i(TAG, "DONE");
    }

    private String copyAsset(String name) throws Exception {
        java.io.File out = new java.io.File(getFilesDir(), name);
        try (InputStream in = getAssets().open(name); java.io.FileOutputStream fo = new java.io.FileOutputStream(out)) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) fo.write(buf, 0, r);
        }
        return out.getAbsolutePath();
    }

    private static final class Result {
        float[] out;
        double err;
        double ms;

        double diffVsCpu(Result cpu) {
            double d = 0;
            for (int i = 0; i < out.length; i++) d = Math.max(d, Math.abs(out[i] - cpu.out[i]));
            return d;
        }
    }

    private Result infer(String path, float[] x, boolean nnapi) throws Exception {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions o = new OrtSession.SessionOptions()) {
            if (nnapi) {
                o.addNnapi();
            }
            try (OrtSession s = env.createSession(path, o);
                 OnnxTensor in = OnnxTensor.createTensor(env, FloatBuffer.wrap(x), new long[] {1, 3, 80, 80})) {
                String name = s.getInputNames().iterator().next();
                Result r = new Result();
                for (int w = 0; w < 5; w++) {
                    s.run(Collections.singletonMap(name, in)).close(); // warm-up
                }
                long t0 = System.nanoTime();
                int n = 30;
                float[] last = null;
                for (int i = 0; i < n; i++) {
                    try (OrtSession.Result res = s.run(Collections.singletonMap(name, in))) {
                        last = ((float[][]) res.get(0).getValue())[0];
                    }
                }
                r.ms = (System.nanoTime() - t0) / 1e6 / n;
                r.out = last;
                for (int i = 0; i < REF.length; i++) r.err = Math.max(r.err, Math.abs(last[i] - REF[i]));
                return r;
            }
        }
    }
}
