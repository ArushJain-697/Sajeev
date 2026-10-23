// #51 (WS3) | Consumes the SBI app's frame feed (the stream extension, #50) as a FrameSource. Java 8 source level.
package io.sajeev.engine.android;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import io.sajeev.api.DeviceException;
import io.sajeev.api.ErrorState;
import io.sajeev.api.Frame;
import io.sajeev.api.FrameSource;
import io.sajeev.api.StreamHealth;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Binds to the SBI app's bound service, subscribes, ACKs every frame (the service drops frames for a slow client, so a
 * slow engine never stalls the SBI preview) and re-binds when the service dies. Frames are queued (drop-oldest).
 * Message numbers and keys are those of patch 0003 (MockAndroidSBI frame feed).
 */
public final class AndroidSbiStreamSource implements FrameSource {

    static final int MSG_SUBSCRIBE = 1;
    static final int MSG_UNSUBSCRIBE = 2;
    static final int MSG_FRAME = 3;
    static final int MSG_ACK = 4;

    private final Context context;
    private final ComponentName service;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Messenger me = new Messenger(new Handler(Looper.getMainLooper(), this::onMessage));
    private final BlockingQueue<Frame> queue = new ArrayBlockingQueue<>(8);
    private final ArrayDeque<Long> arrivals = new ArrayDeque<>();
    private volatile Messenger remote;
    private volatile boolean closed;
    private volatile boolean mockSeen;
    private volatile int restarts;
    private long seen;
    private long gaps;
    private long lastArrival;

    /**
     * @param context any context
     * @param packageName the SBI app's package
     * @param serviceClass the feed service class name
     */
    public AndroidSbiStreamSource(Context context, String packageName, String serviceClass) {
        this.context = context.getApplicationContext();
        this.service = new ComponentName(packageName, serviceClass);
    }

    /** Binds to the feed. @throws DeviceException DEVICE_UNAVAILABLE when the service is missing or the permission is denied */
    public void start() throws DeviceException {
        try {
            if (!bind()) {
                throw new DeviceException(ErrorState.DEVICE_UNAVAILABLE, "frame feed service not found");
            }
        } catch (SecurityException e) {
            throw new DeviceException(ErrorState.DEVICE_UNAVAILABLE, "frame feed not allowed: " + e.getMessage());
        }
    }

    private boolean bind() {
        Intent i = new Intent().setComponent(service);
        return context.bindService(i, connection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            remote = new Messenger(binder);
            send(MSG_SUBSCRIBE);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            remote = null;
            if (!closed) {
                restarts++;
                // the system keeps the binding and reconnects when the service is back; nothing else to do
            }
        }

        @Override
        public void onBindingDied(ComponentName name) {
            remote = null;
            if (!closed) {
                restarts++;
                context.unbindService(this);
                main.postDelayed(() -> {
                    if (!closed) {
                        bind();
                    }
                }, 500);
            }
        }
    };

    private void send(int what) {
        Messenger r = remote;
        if (r == null) {
            return;
        }
        try {
            Message m = Message.obtain(null, what);
            m.replyTo = me;
            r.send(m);
        } catch (Exception ignored) {
            // the service went away; the connection callback handles it
        }
    }

    private boolean onMessage(Message msg) {
        if (msg.what != MSG_FRAME) {
            return false;
        }
        Bundle d = msg.getData();
        byte[] jpeg = d.getByteArray("jpeg");
        send(MSG_ACK); // ACK first: backpressure is the service's job
        if (jpeg == null || jpeg.length == 0) {
            return true;
        }
        mockSeen |= d.getBoolean("mock");
        long now = System.nanoTime();
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
        Frame f = new Frame(jpeg, now, d.getLong("seq"));
        while (!queue.offer(f)) {
            queue.poll();
        }
        return true;
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

    /** @return true if the service marks its frames as MOCK (the engine must then label the session MOCK) */
    public boolean isMock() {
        return mockSeen;
    }

    /** @return how many times the service connection was lost and re-established */
    public int restarts() {
        return restarts;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        send(MSG_UNSUBSCRIBE);
        try {
            context.unbindService(connection);
        } catch (IllegalArgumentException ignored) {
            // not bound
        }
        queue.clear();
    }
}
