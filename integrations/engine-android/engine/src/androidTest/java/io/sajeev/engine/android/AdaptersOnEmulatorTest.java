// #51 (WS3) | Instrumented tests for the two Android adapters. The stream test needs the patched MockAndroidSBI installed.
package io.sajeev.engine.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import io.sajeev.api.Frame;
import io.sajeev.api.Verdict;
import io.sajeev.engine.EngineModels;
import java.io.File;
import java.io.InputStream;
import java.util.Optional;
import org.junit.Test;

public class AdaptersOnEmulatorTest {

    private static int count(AndroidSbiStreamSource s, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        int n = 0;
        while (System.currentTimeMillis() < end) {
            Optional<Frame> f = s.next(200);
            if (f.isPresent()) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void test_capture_only_device_is_labelled_limited() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getContext();
        AndroidImageDecoder.install();
        File models = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), "models");
        EngineModelsFiles.copy(c, models);
        try (EngineModels m = new EngineModels(models.toPath())) {
            CaptureOnlyChecker k = new CaptureOnlyChecker(m.detector(), m.minifasV2(), m.minifasV1se());
            byte[] jpg;
            try (InputStream in = c.getAssets().open("one_face.jpg")) {
                java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
                byte[] b = new byte[65536];
                int r;
                while ((r = in.read(b)) > 0) {
                    o.write(b, 0, r);
                }
                jpg = o.toByteArray();
            }
            CaptureOnlyChecker.Result r = k.check(jpg);
            assertEquals(Verdict.Tier.LIMITED, r.tier);
            assertFalse("a photo must not pass even the limited check", r.passed);
            assertEquals(CaptureOnlyChecker.Result.class, r.getClass());
            CaptureOnlyChecker.Result bad = k.check(new byte[] {1, 2, 3});
            assertEquals(Verdict.Tier.LIMITED, bad.tier);
            assertFalse(bad.passed);
        }
    }

    @Test
    public void test_stream_adapter_survives_service_restart() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AndroidSbiStreamSource s = new AndroidSbiStreamSource(c, "io.mosip.mock.sbi", "io.mosip.mock.sbi.feed.FrameFeedService");
        s.start();
        int before = count(s, 3000);
        assertTrue("no frames from the feed: " + before, before > 5);
        assertTrue("the feed must be flagged MOCK", s.isMock());
        UiAutomation ua = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        ua.executeShellCommand("am force-stop io.mosip.mock.sbi").close();
        Thread.sleep(1500);
        int after = count(s, 8000);
        android.util.Log.i("SAJEEV", "stream before=" + before + " after restart=" + after + " restarts=" + s.restarts()
                + " fps=" + s.health().measuredFps());
        assertTrue("frames did not resume after the SBI service restarted: " + after, after > 5);
        assertTrue(s.restarts() >= 1);
        s.close();
    }
}
