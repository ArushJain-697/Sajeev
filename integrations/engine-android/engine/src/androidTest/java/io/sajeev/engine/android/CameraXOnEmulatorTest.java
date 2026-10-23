// #49 (WS3) | Instrumented tests for the CameraX source. The emulator's front camera is a virtual scene or the Mac webcam.
package io.sajeev.engine.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.content.Context;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.test.platform.app.InstrumentationRegistry;
import io.sajeev.api.DeviceException;
import io.sajeev.api.ErrorState;
import io.sajeev.api.Frame;
import java.util.Optional;
import org.junit.Test;

public class CameraXOnEmulatorTest {

    /** A lifecycle that is already RESUMED, standing in for an activity. */
    private static final class Resumed implements LifecycleOwner {
        private final LifecycleRegistry registry = new LifecycleRegistry(this);

        Resumed() {
            registry.markState(Lifecycle.State.RESUMED);
        }

        @Override
        public Lifecycle getLifecycle() {
            return registry;
        }
    }

    private static LifecycleOwner started() throws Exception {
        final LifecycleOwner[] o = new LifecycleOwner[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> o[0] = new Resumed());
        return o[0];
    }

    @Test
    public void test_camera_permission_denied_reports_camera_unavailable() throws Exception {
        // A context that answers "denied" for the camera: revoking a granted permission would kill the test process.
        Context real = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context denied = new android.content.ContextWrapper(real) {
            @Override
            public int checkSelfPermission(String permission) {
                return Manifest.permission.CAMERA.equals(permission) ? android.content.pm.PackageManager.PERMISSION_DENIED
                        : super.checkSelfPermission(permission);
            }

            @Override
            public Context getApplicationContext() {
                return this;
            }
        };
        CameraXFrameSource s = new CameraXFrameSource(denied, started(), 15);
        try {
            s.start();
            fail("start() must refuse without the camera permission");
        } catch (DeviceException e) {
            assertEquals(ErrorState.CAMERA_UNAVAILABLE, e.state());
        } finally {
            s.close();
        }
    }

    @Test
    public void test_frames_arrive_at_target_rate_within_20pct() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(c.getPackageName(), Manifest.permission.CAMERA);
        // the AVD has only a back camera (hw.camera.front = none), so the rate test uses it; the front camera path differs only in the selector
        CameraXFrameSource s = new CameraXFrameSource(c, started(), 15, false);
        s.start();
        try {
            Thread.sleep(1500); // warm-up
            long end = System.currentTimeMillis() + 6000;
            int n = 0;
            while (System.currentTimeMillis() < end) {
                Optional<Frame> f = s.next(300);
                if (f.isPresent()) {
                    n++;
                    assertTrue(f.get().jpeg().length > 1000);
                }
            }
            double fps = n / 6.0;
            android.util.Log.i("SAJEEV", "camerax fps=" + fps + " health=" + s.health().measuredFps());
            assertTrue("rate " + fps + " is not within 20% of 15", fps >= 12 && fps <= 18);
        } finally {
            s.close();
        }
    }
}
