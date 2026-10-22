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
    }

    @Override
    public EngineSession start(Workflow workflow, PolicyConfig policy) {
        return new EngineSession(this, workflow, policy);
    }

    @Override
    public void close() {
        workers.shutdownNow();
        models.close();
    }
}
