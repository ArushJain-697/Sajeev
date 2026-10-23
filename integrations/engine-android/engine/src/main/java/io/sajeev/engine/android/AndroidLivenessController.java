// #52 (WS3) | Runs one liveness session on Android: SBI frame feed -> engine -> progress events. No Flutter types here. Java 8 source level.
package io.sajeev.engine.android;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import io.sajeev.api.ChallengeType;
import io.sajeev.api.ErrorState;
import io.sajeev.api.Frame;
import io.sajeev.api.PolicyConfig;
import io.sajeev.api.Verdict;
import io.sajeev.api.Workflow;
import io.sajeev.audit.AuditLog;
import io.sajeev.binding.Binding;
import io.sajeev.engine.EngineModels;
import io.sajeev.engine.EngineSession;
import io.sajeev.engine.EngineSettings;
import io.sajeev.engine.LivenessEngine;
import io.sajeev.guidance.GuidanceKeys;
import io.sajeev.metrics.LivenessMetrics;
import io.sajeev.policy.Config;
import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Starts sessions, pumps frames off the main thread, and reports progress to a listener on the main thread whenever
 * the phase, the challenge or the guidance changes. Engine work never runs on the main thread.
 */
public final class AndroidLivenessController {

    /** What the screen needs. {@code phase} is RUNNING, LIVE, NOT_LIVE, UNAVAILABLE or LIMITED. */
    public static final class Progress {
        public final String phase;
        public final String challengeKey;
        public final String guidanceKey;
        public final String reason;
        public final boolean mock;
        public final boolean limited;

        Progress(String phase, String challengeKey, String guidanceKey, String reason, boolean mock, boolean limited) {
            this.phase = phase;
            this.challengeKey = challengeKey;
            this.guidanceKey = guidanceKey;
            this.reason = reason;
            this.mock = mock;
            this.limited = limited;
        }

        boolean same(Progress o) {
            return o != null && phase.equals(o.phase) && eq(challengeKey, o.challengeKey) && eq(guidanceKey, o.guidanceKey);
        }

        private static boolean eq(String a, String b) {
            return a == null ? b == null : a.equals(b);
        }
    }

    /** Receives progress on the main thread. */
    public interface Listener {
        void onProgress(String sessionId, Progress progress);
    }

    private static final class Running {
        final EngineSession session;
        final AndroidSbiStreamSource source; // null in the capture-only (LIMITED) case
        volatile boolean stop;
        volatile Progress last;

        Running(EngineSession session, AndroidSbiStreamSource source) {
            this.session = session;
            this.source = source;
        }
    }

    private final Context context;
    private final String sbiPackage;
    private final String feedClass;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Running> sessions = new ConcurrentHashMap<>();
    private LivenessEngine engine;

    /**
     * @param context any context
     * @param sbiPackage the SBI app that provides the frame feed
     * @param feedClass the feed service class in that app
     * @param listener progress receiver (main thread); may be null
     */
    public AndroidLivenessController(Context context, String sbiPackage, String feedClass, Listener listener) {
        this.context = context.getApplicationContext();
        this.sbiPackage = sbiPackage;
        this.feedClass = feedClass;
        this.listener = listener;
    }

    private synchronized LivenessEngine engine() throws Exception {
        if (engine == null) {
            AndroidImageDecoder.install();
            File models = new File(context.getFilesDir(), "models");
            engine = new LivenessEngine(new EngineModels(models.toPath()), Config.load(new Properties()),
                    EngineSettings.defaults(), new AuditLog(System::currentTimeMillis), new LivenessMetrics());
        }
        return engine;
    }

    /** Starts a session. Fails closed: if the engine or the feed is missing, the session reports UNAVAILABLE for its whole life. */
    public String start(String workflow) {
        final String id = UUID.randomUUID().toString();
        EngineSession session;
        try {
            session = engine().start(Workflow.valueOf(workflow), new PolicyConfig(0.80, 2, 3));
        } catch (Throwable t) {
            Running dead = new Running(null, null);
            dead.last = new Progress("UNAVAILABLE", null, GuidanceKeys.keyFor(ErrorState.ENGINE_ERROR), "engine_unavailable", false, false);
            sessions.put(id, dead);
            publish(id, dead);
            return id;
        }
        AndroidSbiStreamSource source = new AndroidSbiStreamSource(context, sbiPackage, feedClass);
        final Running r;
        try {
            source.start();
            r = new Running(session, source);
        } catch (Exception e) {
            // no stream extension: only the single-image check is possible (tier LIMITED)
            Running limited = new Running(session, null);
            limited.last = new Progress("LIMITED", null, null, "no_frame_feed", false, true);
            sessions.put(id, limited);
            publish(id, limited);
            return id;
        }
        sessions.put(id, r);
        Thread pump = new Thread(() -> pump(id, r), "sajeev-pump");
        pump.setDaemon(true);
        pump.start();
        return id;
    }

    private void pump(String id, Running r) {
        try {
            while (!r.stop) {
                Optional<Frame> f = r.source.next(100);
                if (f.isPresent()) {
                    r.session.onFrame(f.get());
                }
                Progress p = read(r);
                if (!p.same(r.last)) {
                    r.last = p;
                    publish(id, r);
                }
                if (p.phase.equals("LIVE") || p.phase.equals("NOT_LIVE") || p.phase.equals("UNAVAILABLE")) {
                    r.source.close(); // the stream stops before the device captures (SBI spec)
                    break;
                }
            }
        } catch (Exception e) {
            r.last = new Progress("UNAVAILABLE", null, GuidanceKeys.keyFor(ErrorState.DEVICE_DISCONNECTED), "feed_error", false, false);
            publish(id, r);
        }
    }

    private static Progress read(Running r) {
        boolean mock = r.source != null && r.source.isMock();
        Verdict v = r.session.verdict();
        if (v == null) {
            ChallengeType c = r.session.currentChallenge();
            ErrorState g = r.session.liveGuidance();
            return new Progress("RUNNING", c == null ? null : challengeKey(c), g == null ? "passive.checking" : GuidanceKeys.keyFor(g),
                    "running", mock, false);
        }
        switch (v.tier()) {
            case LIVE_PASSIVE:
            case LIVE_ACTIVE:
                return new Progress("LIVE", null, null, v.reason(), mock, false);
            case ATTACK_SUSPECTED:
            case ACTIVE_REQUIRED:
            case LIMITED:
                return new Progress("NOT_LIVE", null, v.error().isPresent() ? GuidanceKeys.keyFor(v.error().get()) : "failure.liveness",
                        v.reason(), mock, false);
            default:
                return new Progress("UNAVAILABLE", null, v.error().isPresent() ? GuidanceKeys.keyFor(v.error().get()) : "error.engine",
                        v.reason(), mock, false);
        }
    }

    static String challengeKey(ChallengeType c) {
        switch (c) {
            case TURN_HEAD_LEFT:
                return "challenge.turn_left";
            case TURN_HEAD_RIGHT:
                return "challenge.turn_right";
            default:
                return "challenge." + c.name().toLowerCase();
        }
    }

    private void publish(final String id, final Running r) {
        final Progress p = r.last;
        if (listener != null && p != null) {
            main.post(() -> listener.onProgress(id, p));
        }
    }

    /** @return the latest progress; UNAVAILABLE for an unknown id */
    public Progress progress(String id) {
        Running r = sessions.get(id);
        if (r == null) {
            return new Progress("UNAVAILABLE", null, "error.engine", "unknown_session", false, false);
        }
        if (r.session == null || r.last != null && !r.last.phase.equals("RUNNING")) {
            return r.last;
        }
        r.last = read(r);
        return r.last;
    }

    /** True only when the session is LIVE and the captured face is bound to it. The limited tier never returns true here. */
    public boolean checkCapture(String id, byte[] image, long timeoutMs) {
        Running r = sessions.get(id);
        if (r == null || r.session == null || r.source == null && (r.last == null || !r.last.limited)) {
            return false;
        }
        if (r.last != null && r.last.limited) {
            return false; // a capture-only session has no live face to bind to
        }
        Binding.Result b = r.session.checkCapture(image, System.nanoTime(), timeoutMs);
        return b.verdict() == Binding.Verdict.BOUND && "LIVE".equals(read(r).phase);
    }

    public void abort(String id) {
        Running r = sessions.remove(id);
        if (r != null) {
            r.stop = true;
            if (r.source != null) {
                r.source.close();
            }
            if (r.session != null) {
                r.session.abort();
            }
        }
    }

    public synchronized void shutdown() {
        for (String id : sessions.keySet()) {
            abort(id);
        }
        if (engine != null) {
            engine.close();
            engine = null;
        }
    }
}
