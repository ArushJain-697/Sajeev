// #48 (WS3) | Instrumented tests, run on the emulator. Models and a face photo are copied to test assets by prepare.sh.
package io.sajeev.engine.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import io.sajeev.api.Frame;
import io.sajeev.api.PolicyConfig;
import io.sajeev.api.Verdict;
import io.sajeev.api.Workflow;
import io.sajeev.audit.AuditLog;
import io.sajeev.engine.EngineModels;
import io.sajeev.engine.EngineSession;
import io.sajeev.engine.EngineSettings;
import io.sajeev.engine.LivenessEngine;
import io.sajeev.metrics.LivenessMetrics;
import io.sajeev.policy.Config;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import org.junit.Test;

public class EngineOnEmulatorTest {

    private static void copyAssets(Context c, String dir, File to) throws IOException {
        String[] names = c.getAssets().list(dir);
        to.mkdirs();
        for (String n : names) {
            String[] sub = c.getAssets().list(dir + "/" + n);
            if (sub != null && sub.length > 0) {
                copyAssets(c, dir + "/" + n, new File(to, n));
            } else {
                try (InputStream in = c.getAssets().open(dir + "/" + n); OutputStream out = new FileOutputStream(new File(to, n))) {
                    byte[] buf = new byte[65536];
                    int r;
                    while ((r = in.read(buf)) > 0) {
                        out.write(buf, 0, r);
                    }
                }
            }
        }
    }

    private static byte[] face(Context c) throws IOException {
        try (InputStream in = c.getAssets().open("one_face.jpg")) {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) {
                o.write(buf, 0, r);
            }
            return o.toByteArray();
        }
    }

    @Test
    public void test_engine_runs_on_emulator() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getContext();
        AndroidImageDecoder.install();
        File models = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), "models");
        copyAssets(c, "models", models);
        byte[] jpg = face(c);
        try (LivenessEngine engine = new LivenessEngine(new EngineModels(models.toPath()), Config.load(new Properties()),
                EngineSettings.defaults(), new AuditLog(System::currentTimeMillis), new LivenessMetrics())) {
            EngineSession s = engine.start(Workflow.RESIDENT, new PolicyConfig(0.80, 2, 3));
            long t0 = 1_000_000_000L;
            for (int i = 0; i < 40 && s.verdict() == null; i++) {
                s.onFrame(new Frame(jpg, t0 + i * 100_000_000L, i));
                Thread.sleep(40);
            }
            long end = System.currentTimeMillis() + 20000;
            while (s.verdict() == null && System.currentTimeMillis() < end) {
                Thread.sleep(100);
            }
            Verdict v = s.verdict();
            assertNotNull("no verdict on the emulator", v);
            android.util.Log.i("SAJEEV", "emulator verdict " + v.tier() + " " + v.error() + " votes=" + s.passiveVotes().keySet());
            assertEquals("five voters ran", 5, s.passiveVotes().size());
            assertTrue("a held-still photo must not be live", v.tier() != Verdict.Tier.LIVE_PASSIVE && v.tier() != Verdict.Tier.LIVE_ACTIVE);
        }
    }
}
