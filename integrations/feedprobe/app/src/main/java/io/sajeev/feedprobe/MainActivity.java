// SAJEEV #50 test probe (test tooling only, not shipped). Binds to the mock's frame feed and logs what it receives.
package io.sajeev.feedprobe;

import android.app.Activity;
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
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "PROBE";
    private Messenger service;
    private Messenger me;
    private final List<Long> arrivals = new ArrayList<>();
    private long mockFrames;
    private boolean slow;
    private long seconds;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        seconds = getIntent().getIntExtra("seconds", 5);
        slow = getIntent().getBooleanExtra("slow", false);
        me = new Messenger(new Handler(Looper.getMainLooper(), this::onMsg));
        Intent i = new Intent();
        i.setComponent(new ComponentName("io.mosip.mock.sbi", "io.mosip.mock.sbi.feed.FrameFeedService"));
        boolean ok;
        try {
            ok = bindService(i, conn, Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            Log.i(TAG, "RESULT bind=DENIED reason=SecurityException " + e.getMessage());
            finish();
            return;
        }
        if (!ok) {
            Log.i(TAG, "RESULT bind=DENIED reason=bindService_returned_false");
            finish();
        }
    }

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName n, IBinder binder) {
            service = new Messenger(binder);
            send(1); // MSG_SUBSCRIBE
            main.postDelayed(() -> {
                send(5); // MSG_STATS
                main.postDelayed(() -> finishUp(), 800);
            }, seconds * 1000);
        }

        @Override
        public void onServiceDisconnected(ComponentName n) {
            service = null;
        }
    };

    private void send(int what) {
        try {
            Message m = Message.obtain(null, what);
            m.replyTo = me;
            service.send(m);
        } catch (Exception e) {
            Log.i(TAG, "send failed " + e);
        }
    }

    private boolean onMsg(Message msg) {
        if (msg.what == 3) { // MSG_FRAME
            long now = System.nanoTime();
            arrivals.add(now);
            Bundle d = msg.getData();
            if (d.getBoolean("mock")) {
                mockFrames++;
            }
            if (slow) {
                try {
                    Thread.sleep(400); // a slow client: it ACKs late
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            send(4); // MSG_ACK
            return true;
        }
        if (msg.what == 5) { // MSG_STATS
            Bundle d = msg.getData();
            Log.i(TAG, "STATS sent=" + d.getLong("sent") + " dropped=" + d.getLong("dropped") + " fps=" + d.getInt("fps"));
            return true;
        }
        return false;
    }

    private void finishUp() {
        double fps = 0;
        if (arrivals.size() >= 2) {
            double span = (arrivals.get(arrivals.size() - 1) - arrivals.get(0)) / 1e9;
            fps = (arrivals.size() - 1) / span;
        }
        Log.i(TAG, "RESULT bind=OK frames=" + arrivals.size() + " mockFlagged=" + mockFrames
                + " measuredFps=" + String.format("%.1f", fps) + " slow=" + slow);
        send(2); // MSG_UNSUBSCRIBE
        unbindService(conn);
        finish();
    }
}
