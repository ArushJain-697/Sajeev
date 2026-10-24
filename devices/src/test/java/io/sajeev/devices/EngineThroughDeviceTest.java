// #39 part (b) (WS4) | The whole engine fed from a real device stream (MockMDS over HTTP through Sbi10HttpAdapter).
// Skips unless a MockMDS answers on port 4501 and the models are present. The run script eval/rig/run_engine_via_device.sh
// starts the mock replaying a face photo at a chosen fps and sets -Dsajeev.device.expect to NOT_LIVE or BELOW_FPS.
package io.sajeev.devices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.sajeev.api.ErrorState;
import io.sajeev.api.Frame;
import io.sajeev.api.FrameSource;
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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class EngineThroughDeviceTest {

    private static boolean mockUp() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 4501), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void test_engine_on_a_device_stream_never_calls_a_replayed_photo_live() throws Exception {
        String models = System.getProperty("sajeev.models", System.getenv("SAJEEV_MODELS"));
        String expect = System.getProperty("sajeev.device.expect");
        assumeTrue(models != null && expect != null && mockUp(), "needs models, a running MockMDS and -Dsajeev.device.expect");
        Sbi10HttpAdapter a = new Sbi10HttpAdapter("127.0.0.1", 4501, 4600, 15000);
        String id = a.discover().get(0).id();
        try (LivenessEngine engine = new LivenessEngine(new EngineModels(Paths.get(models)), Config.load(new Properties()),
                EngineSettings.defaults(), new AuditLog(System::currentTimeMillis), new LivenessMetrics());
             FrameSource src = a.openStream(id)) {
            EngineSession s = engine.start(Workflow.RESIDENT, new PolicyConfig(0.80, 2, 3));
            long end = System.currentTimeMillis() + 60_000;
            int fed = 0;
            while (s.verdict() == null && System.currentTimeMillis() < end) {
                Optional<Frame> f = src.next(500);
                if (f.isPresent()) {
                    s.onFrame(f.get());
                    fed++;
                }
            }
            Verdict v = s.verdict();
            assertNotNull(v, "no verdict after " + fed + " frames from the device");
            System.out.println("DEVICE RUN expect=" + expect + " fed=" + fed + " measured fps=" + src.health().measuredFps()
                    + " engine-measured fps=" + s.measuredFps() + " verdict=" + v.tier() + " " + v.error() + " " + v.reason());
            assertTrue(v.tier() != Verdict.Tier.LIVE_PASSIVE && v.tier() != Verdict.Tier.LIVE_ACTIVE, "a replayed photo was judged live");
            if (expect.equals("BELOW_FPS")) {
                assertEquals(Verdict.Tier.CANNOT_JUDGE, v.tier());
                assertEquals(ErrorState.INVALID_FRAME_DATA, v.error().get());
            }
            s.abort();
        }
    }
}
