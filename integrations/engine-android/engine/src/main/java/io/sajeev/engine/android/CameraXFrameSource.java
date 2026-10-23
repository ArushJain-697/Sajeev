// #49 (WS3) | Optional camera source: frames straight from the phone's front camera through CameraX, for devices without an SBI
// frame feed. Lifecycle-aware. Java 8 source level. Without the CAMERA permission it reports CAMERA_UNAVAILABLE.
package io.sajeev.engine.android;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Size;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;
import io.sajeev.api.DeviceException;
import io.sajeev.api.ErrorState;
import io.sajeev.api.Frame;
import io.sajeev.api.FrameSource;
import io.sajeev.api.StreamHealth;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Binds an ImageAnalysis use case to the front camera and the given lifecycle (CameraX unbinds and rebinds with the
 * lifecycle, so rotation and backgrounding are handled by the owner). Frames are JPEG, mirrored as the front camera shows them
 * is NOT applied: the engine sees the raw sensor image rotated upright. Target rate is limited by dropping frames.
 */
public final class CameraXFrameSource implements FrameSource {

    private final Context context;
    private final LifecycleOwner owner;
    private final int targetFps;
    private final boolean front;
    private final BlockingQueue<Frame> queue = new ArrayBlockingQueue<>(8);
    private final ArrayDeque<Long> arrivals = new ArrayDeque<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ProcessCameraProvider provider;
    private volatile boolean closed;
    private long seen;
    private long gaps;
    private long lastArrival;
    private long lastKept;
    private long seq;

    /**
     * @param context any context
     * @param owner the lifecycle the camera is bound to
     * @param targetFps frames per second to deliver (15 is the plan); extra camera frames are dropped
     */
    public CameraXFrameSource(Context context, LifecycleOwner owner, int targetFps) {
        this(context, owner, targetFps, true);
    }

    /** Same, choosing the camera: {@code front=false} uses the back camera (only for testing on an emulator that has no front camera). */
    public CameraXFrameSource(Context context, LifecycleOwner owner, int targetFps, boolean front) {
        this.front = front;
        this.context = context.getApplicationContext();
        this.owner = owner;
        this.targetFps = Math.max(1, targetFps);
    }

    /** @throws DeviceException CAMERA_UNAVAILABLE without the permission or when no front camera can be bound */
    public void start() throws DeviceException {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            throw new DeviceException(ErrorState.CAMERA_UNAVAILABLE, "camera permission not granted");
        }
        try {
            ListenableFuture<ProcessCameraProvider> f = ProcessCameraProvider.getInstance(context);
            provider = f.get(10, TimeUnit.SECONDS);
            ImageAnalysis analysis = new ImageAnalysis.Builder().setTargetResolution(new Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
            analysis.setAnalyzer(executor, this::analyze);
            final ProcessCameraProvider p = provider;
            final ImageAnalysis a = analysis;
            android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
            final Exception[] err = new Exception[1];
            final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            main.post(() -> {
                try {
                    p.unbindAll();
                    p.bindToLifecycle(owner, front ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA, a);
                } catch (Exception e) {
                    err[0] = e;
                }
                done.countDown();
            });
            if (!done.await(10, TimeUnit.SECONDS) || err[0] != null) {
                throw new DeviceException(ErrorState.CAMERA_UNAVAILABLE, "cannot bind the front camera: " + err[0]);
            }
        } catch (DeviceException e) {
            throw e;
        } catch (ExecutionException | java.util.concurrent.TimeoutException | InterruptedException | RuntimeException e) {
            throw new DeviceException(ErrorState.CAMERA_UNAVAILABLE, "camera start failed: " + e);
        }
    }

    private void analyze(@NonNull ImageProxy image) {
        try {
            long now = System.nanoTime();
            if (closed || now - lastKept < 1_000_000_000L / targetFps) {
                return; // drop to the target rate
            }
            lastKept = now;
            byte[] jpeg = toJpeg(image);
            if (jpeg == null) {
                return;
            }
            synchronized (arrivals) {
                if (lastArrival != 0 && now - lastArrival > 1_000_000_000L) {
                    gaps++;
                }
                lastArrival = now;
                arrivals.addLast(now);
                while (arrivals.size() > 30) {
                    arrivals.removeFirst();
                }
                seen++;
            }
            Frame f = new Frame(jpeg, now, seq++);
            while (!queue.offer(f)) {
                queue.poll();
            }
        } finally {
            image.close();
        }
    }

    /** YUV_420_888 to NV21 to JPEG, rotated upright using the rotation CameraX reports (rotation applied by the viewer). */
    static byte[] toJpeg(ImageProxy image) {
        if (image.getFormat() != ImageFormat.YUV_420_888 || image.getPlanes().length < 3) {
            return null;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        byte[] nv21 = new byte[w * h * 3 / 2];
        ImageProxy.PlaneProxy yP = image.getPlanes()[0];
        ImageProxy.PlaneProxy uP = image.getPlanes()[1];
        ImageProxy.PlaneProxy vP = image.getPlanes()[2];
        ByteBuffer y = yP.getBuffer();
        int pos = 0;
        for (int row = 0; row < h; row++) { // respect the row stride: the plane can be wider than the image
            y.position(row * yP.getRowStride());
            y.get(nv21, pos, w);
            pos += w;
        }
        ByteBuffer u = uP.getBuffer();
        ByteBuffer v = vP.getBuffer();
        for (int row = 0; row < h / 2; row++) {
            for (int col = 0; col < w / 2; col++) {
                nv21[pos++] = v.get(row * vP.getRowStride() + col * vP.getPixelStride());
                nv21[pos++] = u.get(row * uP.getRowStride() + col * uP.getPixelStride());
            }
        }
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, w, h, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuv.compressToJpeg(new Rect(0, 0, w, h), 90, out);
        byte[] jpeg = out.toByteArray();
        int rot = image.getImageInfo().getRotationDegrees();
        if (rot == 0) {
            return jpeg;
        }
        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.postRotate(rot);
        android.graphics.Bitmap r = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        ByteArrayOutputStream o2 = new ByteArrayOutputStream();
        r.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, o2);
        bmp.recycle();
        r.recycle();
        return o2.toByteArray();
    }

    @Override
    public Optional<Frame> next(long timeoutMillis) throws DeviceException {
        if (closed) {
            throw new DeviceException(ErrorState.DEVICE_DISCONNECTED, "source closed");
        }
        try {
            return Optional.ofNullable(queue.poll(timeoutMillis, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    @Override
    public StreamHealth health() {
        synchronized (arrivals) {
            double fps = 0;
            if (arrivals.size() >= 2) {
                fps = (arrivals.size() - 1) / ((arrivals.peekLast() - arrivals.peekFirst()) / 1e9);
            }
            return new StreamHealth(fps, seen, gaps);
        }
    }

    @Override
    public void close() {
        closed = true;
        final ProcessCameraProvider p = provider;
        if (p != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(p::unbindAll);
        }
        executor.shutdown();
        queue.clear();
    }
}
