// #34 (WS1) | Bible §6, §9 | The engine facade: the single entry point that clients call (PDF IN1). Java 11 only.
// Models are loaded once and shared. Each session runs on its own daemon thread behind a bounded drop-oldest queue, so
// the frame producer is never blocked. No network class is referenced anywhere in the engine (checked by a test).
package io.sajeev.engine;

import io.sajeev.api.LivenessService;
import io.sajeev.api.PolicyConfig;
import io.sajeev.api.Workflow;
import io.sajeev.audit.AuditLog;
import io.sajeev.metrics.LivenessMetrics;
import io.sajeev.policy.Config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Creates liveness sessions over shared models. */
public final class LivenessEngine implements LivenessService, AutoCloseable {

    final EngineModels models;
    final Config config;
    final EngineSettings settings;
    final AuditLog audit;
    final LivenessMetrics metrics;
    final ExecutorService workers = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sajeev-session");
        t.setDaemon(true);
        return t;
    });

    /**
     * @param models the loaded models (owned by the engine from now on)
     * @param config the validated configuration
     * @param settings engine tuning
     * @param audit the audit log
     * @param metrics the aggregate metrics
     */
    public LivenessEngine(EngineModels models, Config config, EngineSettings settings, AuditLog audit,
                          LivenessMetrics metrics) {
        this.models = models;
        this.config = config;
        this.settings = settings;
        this.audit = audit;
        this.metrics = metrics;
        io.sajeev.image.ImageCodec.setLimits(settings.maxImageSide, settings.maxImagePixels);
    }

    private final java.util.concurrent.atomic.AtomicInteger running = new java.util.concurrent.atomic.AtomicInteger();

    /** @return true when a session slot was taken (the session must call {@link #releaseSlot()} exactly once when it ends) */
    boolean tryAcquireSlot() {
        while (true) {
            int n = running.get();
            if (n >= settings.maxConcurrentSessions) {
                return false;
            }
            if (running.compareAndSet(n, n + 1)) {
                return true;
            }
        }
    }

    void releaseSlot() {
        running.decrementAndGet();
    }

    /** @return how many sessions are running now (a session that has ended and released its worker is not counted) */
    public int runningSessions() {
        return running.get();
    }

    @Override
    public EngineSession start(Workflow workflow, PolicyConfig policy) {
        return new EngineSession(this, workflow, policy);
    }

    /** Starts a session with the workflow's own policy from the configuration (what the clients use). */
    public EngineSession start(Workflow workflow) {
        return start(workflow, policyFor(workflow));
    }

    /**
     * @param workflow resident, operator or supervisor
     * @return threshold, minimum challenges and retries for this workflow, per-workflow overrides included
     */
    public PolicyConfig policyFor(Workflow workflow) {
        String w = workflow.name().toLowerCase(java.util.Locale.ROOT);
        return new PolicyConfig(Double.parseDouble(config.value("passive.threshold", w)),
                Integer.parseInt(config.value("active.minChallenges", w)), Integer.parseInt(config.value("maxRetries", w)));
    }

    @Override
    public void close() {
        workers.shutdownNow();
        models.close();
    }
}
