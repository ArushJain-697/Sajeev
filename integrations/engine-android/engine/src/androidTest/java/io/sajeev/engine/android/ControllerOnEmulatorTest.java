// #52 (WS3) | The controller feeds the engine from the mock SBI feed and pushes state events. Emulator, mock SBI installed.
package io.sajeev.engine.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ControllerOnEmulatorTest {

    @Test
    public void test_state_events_reach_the_listener_and_a_synthetic_feed_is_never_live() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context t = InstrumentationRegistry.getInstrumentation().getContext();
        EngineModelsFiles.copy(t, new File(c.getFilesDir(), "models"));
        final List<String> phases = Collections.synchronizedList(new ArrayList<String>());
        AndroidLivenessController ctl = new AndroidLivenessController(c, "io.mosip.mock.sbi",
                "io.mosip.mock.sbi.feed.FrameFeedService", (id, p) -> phases.add(p.phase + "/" + p.guidanceKey + "/mock=" + p.mock));
        String id = ctl.start("RESIDENT");
        long end = System.currentTimeMillis() + 40000;
        AndroidLivenessController.Progress p = ctl.progress(id);
        while (p.phase.equals("RUNNING") && System.currentTimeMillis() < end) {
            Thread.sleep(300);
            p = ctl.progress(id);
        }
        Thread.sleep(500);
        android.util.Log.i("SAJEEV", "controller phases=" + phases + " final=" + p.phase + " reason=" + p.reason);
        assertFalse("no event reached the listener", phases.isEmpty());
        assertNotEquals("a synthetic test pattern was judged live", "LIVE", p.phase);
        assertFalse("capture must be refused when not live", ctl.checkCapture(id, new byte[] {1, 2, 3}, 3000));
        assertEquals("UNAVAILABLE", ctl.progress("no-such-session").phase);
        ctl.abort(id);
        ctl.shutdown();
    }
}
