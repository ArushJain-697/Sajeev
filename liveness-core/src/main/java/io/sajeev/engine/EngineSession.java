// #34 (WS1) | Bible §6, §9 | One liveness check: IDLE → PASSIVE → ACTIVE → DONE/ABORTED, then the capture is bound. Java 11 only.
// Frames go into a bounded queue (the oldest is dropped when it is full); one worker thread runs the pipeline:
// Frame Guard → decode → YuNet → quality → landmarks → five voters → Decision Gate → challenges + re-evaluation.
// onFrame() never blocks. After DONE or ABORTED no frame is kept: only the small Binding evidence (embeddings).
package io.sajeev.engine;

import io.sajeev.active.ActionDetectors;
import io.sajeev.active.ActionThresholds;
import io.sajeev.active.ChallengeEngine;
import io.sajeev.active.ChallengePlanner;
import io.sajeev.active.ChallengeReevaluator;
import io.sajeev.active.ChallengeSession;
import io.sajeev.active.FrameFeatures;
import io.sajeev.api.ChallengeType;
import io.sajeev.api.ErrorState;
import io.sajeev.api.Frame;
import io.sajeev.api.LivenessSession;
import io.sajeev.api.PadVoter;
import io.sajeev.api.PolicyConfig;
import io.sajeev.api.Verdict;
import io.sajeev.api.Vote;
import io.sajeev.api.Workflow;
import io.sajeev.audit.LivenessAuditEvent;
import io.sajeev.binding.Binding;
import io.sajeev.binding.FaceAlignment;
import io.sajeev.binding.GeometryRatios;
import io.sajeev.face.Detection;
import io.sajeev.face.FaceFeatures;
import io.sajeev.face.FaceQuality;
import io.sajeev.face.FaceTracker;
import io.sajeev.frame.FrameFlag;
import io.sajeev.frame.FrameGuard;
import io.sajeev.frame.ProcessingMode;
import io.sajeev.gate.DecisionGate;
import io.sajeev.model.LoadShedder;
import io.sajeev.passive.edge.FrameEdgeVoter;
import io.sajeev.passive.flatness.FlatnessVoter;
import io.sajeev.passive.moire.MoireVoter;
import io.sajeev.passive.reflection.ReflectionVoter;
import io.sajeev.passive.texture.TextureCnnVoter;
import io.sajeev.policy.PolicyEngine;

import io.sajeev.image.ImageCodec;
import io.sajeev.image.PixelImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/** The engine's session. Not part of the frozen API beyond {@link LivenessSession}; the extra methods serve the clients and the bench. */
public final class EngineSession implements LivenessSession {

    /** Lowest measured fps at which a BLINK challenge may be issued. Set from the blink recall experiment (eval/blink, #61). */
    public static final int BLINK_MIN_FPS = 15;

    private final LivenessEngine engine;
    private final Workflow workflow;
    private final PolicyConfig policy;
    private final EngineSettings st;
    private final BlockingQueue<Frame> queue;
    private volatile boolean ended;

    private volatile State state = State.PASSIVE;
    private volatile Verdict verdict;
    private volatile boolean running = true;
    private volatile Binding.Result bindingResult;
    private volatile ChallengeType currentChallenge;

    // ---- worker-thread state ----
    private final FrameGuard guard;
    private final FaceTracker tracker;
    private final Binding binding;
    private final Deque<Frame> usableFrames = new ArrayDeque<>();
    private final Deque<float[][]> usableLandmarks = new ArrayDeque<>();
    private final List<FrameFeatures> usableFeatures = new ArrayList<>();
    private PixelImage lastImage;
    private Detection lastFace;
    private long currentArrivalNanos;
    private long firstArrivalNanos = -1;
    private long usableCount;
    private volatile ErrorState lastTrackerError;
    private final long startedAtMillis = System.currentTimeMillis();
    private double avgProcessingMs;
    private long processed;
    private final LoadShedder shedder = new LoadShedder(8);

    private ChallengeSession challenge;
    private ActionDetectors.Baseline baseline;
    private ActionDetectors.Detector detector;
    private final io.sajeev.face.FrameAnalysis analysis;
    private ChallengeType detectorFor;
    private ChallengeReevaluator reevaluator;
    private final ActionThresholds thresholds = ActionThresholds.defaults();
    private final Map<String, Vote> passiveVotes = new LinkedHashMap<>();

    EngineSession(LivenessEngine engine, Workflow workflow, PolicyConfig policy) {
        this.engine = engine;
        this.workflow = workflow;
        this.policy = policy;
        this.st = engine.settings;
        this.analysis = new io.sajeev.face.FrameAnalysis(engine.models.detector, 12);
        this.queue = new ArrayBlockingQueue<>(st.queueCapacity);
        this.guard = new FrameGuard(() -> currentArrivalNanos, Double.parseDouble(engine.config.value("minFps", name())),
                "PERIODIC".equals(engine.config.value("frameProcessing", name())) ? ProcessingMode.PERIODIC : ProcessingMode.CONTINUOUS, 2);
        this.tracker = new FaceTracker(new FaceTracker.Limits(st.minFaceShare, st.minBrightness, st.minSharpness));
        this.binding = new Binding(parse(engine.config.value("binding.embedding.threshold", name()), 0.363),
                parse(engine.config.value("binding.geometry.tolerance", name()), st.geometryTolerance),
                (long) parse(engine.config.value("binding.windowMarginMs", name()), 2000), 5);
        audit(LivenessAuditEvent.REG_LIVENESS_START, Collections.emptyMap());
        engine.workers.execute(this::run);
    }

    private String name() {
        return workflow.name().toLowerCase();
    }

    private static double parse(String v, double fallback) {
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ public API

    /** Never blocks: when the queue is full the oldest waiting frame is dropped. */
    @Override
    public void onFrame(Frame frame) {
        if (!running) {
            return;
        }
        while (!queue.offer(frame)) {
            queue.poll();
        }
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public Verdict verdict() {
        return verdict;
    }

    @Override
    public void abort() {
        if (state != State.DONE && state != State.ABORTED) {
            verdict = new Verdict(Verdict.Tier.CANNOT_JUDGE, null, "aborted");
            state = State.ABORTED;
        }
        running = false;
        queue.clear();
    }

    /** @return the challenge the client should show now, or null when there is none */
    public ChallengeType currentChallenge() {
        return currentChallenge;
    }

    /** For a UI: the prompt for the current challenge is on screen now. Not needed when autoPrompt is on. */
    public void markPrompted(long nowNanos, long lastFrameSequence) {
        ChallengeSession c = challenge;
        if (c != null) {
            c.prompted(nowNanos, lastFrameSequence);
        }
    }

    /** @return why the newest frames could not be used (no face, many faces, poor quality, bad frames), or null when they are fine */
    public ErrorState liveGuidance() {
        return lastTrackerError;
    }

    /** @return the Binding result once checkCapture ran, else null */
    public Binding.Result bindingResult() {
        return bindingResult;
    }

    /**
     * Check the stored capture against what was seen live (Bible 6.7).
     * @param captureJpeg the captured face as a decodable image (JPEG/PNG; JPEG 2000 from rCapture is decoded by the caller for now)
     * @param arrivalNanos monotonic time (System.nanoTime) when the capture came back
     * @param captureTimeoutMillis the device's capture timeout
     * @return the result; CAPTURE_UNBOUND when there was no live verdict, more or fewer than one face, or any check failed
     */
    public synchronized Binding.Result checkCapture(byte[] captureJpeg, long arrivalNanos, long captureTimeoutMillis) {
        Binding.Result r;
        try {
            PixelImage img = ImageCodec.decode(captureJpeg);
            List<Detection> faces = img == null ? Collections.<Detection>emptyList() : engine.models.detector.detect(img, 0.6, 0.3);
            if (faces.size() == 1) {
                float[][] lm = engine.models.landmarks.landmarks(img, faces.get(0));
                float[] emb = engine.models.sface.embed(FaceAlignment.align(img, lm));
                r = binding.bind(1, emb, GeometryRatios.of(lm), arrivalNanos, captureTimeoutMillis);
            } else {
                r = binding.bind(faces.size(), new float[128], new double[4], arrivalNanos, captureTimeoutMillis);
            }
        } catch (Exception e) {
            r = binding.bind(0, new float[128], new double[4], arrivalNanos, captureTimeoutMillis);
        }
        bindingResult = r;
        Map<String, String> a = new LinkedHashMap<>();
        a.put("result", r.verdict().name());
        a.put("failed", String.valueOf(r.failedChecks().size()));
        audit(LivenessAuditEvent.REG_LIVENESS_BINDING, a);
        return r;
    }

    /** @return how this attempt ended for the policy engine, or null while it is still open (no verdict, or a live verdict waiting for its capture) */
    public PolicyEngine.AttemptResult attemptResult() {
        Verdict v = verdict;
        if (v == null) {
            return null;
        }
        if (v.error().isPresent() && v.error().get() == ErrorState.ENGINE_ERROR) {
            return PolicyEngine.AttemptResult.ENGINE_ERROR;
        }
        switch (v.tier()) {
            case LIVE_PASSIVE:
            case LIVE_ACTIVE:
                if (bindingResult == null) {
                    return null;
                }
                return bindingResult.verdict() == Binding.Verdict.BOUND
                        ? PolicyEngine.AttemptResult.PASSED : PolicyEngine.AttemptResult.UNBOUND;
            case ATTACK_SUSPECTED:
                return v.error().isPresent() && v.error().get() == ErrorState.CHALLENGE_FAILED
                        ? PolicyEngine.AttemptResult.ACTIVE_FAILED : PolicyEngine.AttemptResult.PAD_FAIL;
            case ACTIVE_REQUIRED:
                return PolicyEngine.AttemptResult.ACTIVE_FAILED;
            case LIMITED:
                return PolicyEngine.AttemptResult.NOT_ESTABLISHED;
            default:
                return PolicyEngine.AttemptResult.CANNOT_JUDGE;
        }
    }

    // ------------------------------------------------------------------ worker

    private void run() {
        try {
            while (running && state != State.DONE && state != State.ABORTED) {
                Frame f = queue.poll(50, TimeUnit.MILLISECONDS);
                if (f == null) {
                    continue;
                }
                long t0 = System.nanoTime();
                try {
                    if (st.testDelayMillis > 0) {
                        Thread.sleep(st.testDelayMillis);
                    }
                    process(f);
                } catch (InterruptedException e) {
                    throw e;
                } catch (Exception e) {
                    finish(Verdict.Tier.CANNOT_JUDGE, ErrorState.ENGINE_ERROR, "engine_error:" + e.getClass().getSimpleName());
                }
                double ms = (System.nanoTime() - t0) / 1e6;
                avgProcessingMs = processed == 0 ? ms : 0.8 * avgProcessingMs + 0.2 * ms;
                processed++;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            release();
            ended = true;
        }
    }

    private void release() {
        queue.clear();
        usableFrames.clear();
        usableLandmarks.clear();
        usableFeatures.clear();
        lastImage = null;
        lastFace = null;
        reevaluator = null;
        detector = null;
        analysis.clear();
    }

    private static PixelImage decode(byte[] jpeg) {
        return ImageCodec.decode(jpeg); // null when undecodable
    }

    private void process(Frame f) throws Exception {
        currentArrivalNanos = f.arrivalNanos();
        if (firstArrivalNanos < 0) {
            firstArrivalNanos = f.arrivalNanos();
        }
        PixelImage img = analysis.image(f);
        boolean take = guard.onArrival(img != null);
        if (guard.flags().contains(FrameFlag.INVALID_FRAME_DATA) && img == null) {
            lastTrackerError = ErrorState.INVALID_FRAME_DATA;
        }
        if (img == null || !take) {
            checkPassiveTimeout(f);
            return;
        }
        List<Detection> dets = analysis.faces(f);
        Detection single = dets.size() == 1 ? dets.get(0) : null;
        FaceQuality.Metrics q = FaceQuality.measure(img, single);
        FaceTracker.State ts = tracker.assess(dets, q);
        if (ts.error().isPresent()) {
            lastTrackerError = ts.error().get();
            checkPassiveTimeout(f);
            return;
        }
        lastTrackerError = null;
        float[][] lm = engine.models.landmarks.landmarks(img, ts.face());
        FrameFeatures ff = features(f, lm);
        usableCount++;
        if (usableCount % Math.max(1, st.bindingEveryNth) == 0 || usableCount == 1) {
            float[] emb = engine.models.sface.embed(FaceAlignment.align(img, lm));
            binding.observeLive(emb, GeometryRatios.of(lm), q.sharpness());
        }
        usableFrames.addLast(f);
        usableLandmarks.addLast(lm);
        usableFeatures.add(ff);
        while (usableFrames.size() > st.passiveFrames) {
            usableFrames.removeFirst();
            usableLandmarks.removeFirst();
        }
        lastImage = img;
        lastFace = ts.face();
        if (state == State.PASSIVE) {
            if (usableFrames.size() >= st.passiveFrames) {
                runPassive(f);
            } else {
                checkPassiveTimeout(f);
            }
        } else if (state == State.ACTIVE) {
            runActive(f, ff);
        }
    }

    private static FrameFeatures features(Frame f, float[][] lm) {
        double[] g = FaceFeatures.gaze(lm);
        double ear = (FaceFeatures.eyeAspectRatio(lm, FaceFeatures.EYE_LEFT) + FaceFeatures.eyeAspectRatio(lm, FaceFeatures.EYE_RIGHT)) / 2.0;
        return new FrameFeatures(f.sequence(), f.arrivalNanos(), FaceFeatures.yaw(lm), FaceFeatures.pitch(lm), ear,
                FaceFeatures.mouthAspectRatio(lm), FaceFeatures.mouthWidthRatio(lm), g[0], g[1]);
    }

    private void checkPassiveTimeout(Frame f) {
        if (state == State.PASSIVE
                && f.arrivalNanos() - firstArrivalNanos > st.passiveTimeoutMillis * 1_000_000L) {
            ErrorState e = lastTrackerError != null ? lastTrackerError : ErrorState.FACE_NOT_DETECTED;
            finish(Verdict.Tier.CANNOT_JUDGE, e, "no_usable_face:" + e.name().toLowerCase());
        }
    }

    // ------------------------------------------------------------------ passive

    private double[] weights(int n) {
        String w = engine.config.value("passive.voterWeights", name());
        double[] out = new double[n];
        if ("equal".equals(w)) {
            Arrays.fill(out, 1.0 / n);
            return out;
        }
        String[] parts = w.split(",");
        for (int i = 0; i < n; i++) {
            out[i] = i < parts.length ? Double.parseDouble(parts[i].trim()) : 1.0 / n;
        }
        return out;
    }

    private void runPassive(Frame f) {
        List<Frame> window = new ArrayList<>(usableFrames);
        TextureCnnVoter v1 = new TextureCnnVoter(engine.models.minifasV2, engine.models.minifasV1se, engine.models.detector,
                st.textureLive, st.textureAttack, analysis);
        MoireVoter v2 = new MoireVoter(engine.models.detector, st.moireShare, analysis);
        FlatnessVoter v3 = new FlatnessVoter(st.flatMinYaw, st.flatResidual, 200, 0.03, 42L);
        FrameEdgeVoter v4 = new FrameEdgeVoter(st.edgeMinShare, st.edgeMaxFaceShare, st.edgeMargin);
        ReflectionVoter v5 = new ReflectionVoter(0.02, 0.6, 0.15, 0.95);
        passiveVotes.clear();
        passiveVotes.put(v1.name(), v1.vote(window));
        passiveVotes.put(v2.name(), v2.vote(window));
        passiveVotes.put("V3_flatness", v3.vote(new ArrayList<>(usableLandmarks)));
        passiveVotes.put("V4_frame_edge", v4.vote(lastImage, lastFace));
        passiveVotes.put("V5_reflection", v5.vote(lastImage, lastFace));
        List<Vote> votes = new ArrayList<>(passiveVotes.values());
        boolean activeEnabled = Boolean.parseBoolean(engine.config.value("active.enabled", name()));
        int minLive = Integer.parseInt(engine.config.value("passive.minLiveVotes", name()));
        DecisionGate.Decision g = DecisionGate.decide(votes, weights(votes.size()), true, policy.passiveThreshold(),
                minLive, activeEnabled);
        Map<String, String> a = new LinkedHashMap<>();
        a.put("gate", g.outcome().name());
        a.put("band", io.sajeev.audit.ScoreBand.of(g.passiveScore()).name());
        audit(LivenessAuditEvent.REG_LIVENESS_PASSIVE_RESULT, a);
        switch (g.outcome()) {
            case PASSIVE_PASS:
                binding.liveVerdictAt(System.nanoTime());
                finish(Verdict.Tier.LIVE_PASSIVE, null, "passive_pass");
                break;
            case PAD_FAIL:
                finish(Verdict.Tier.ATTACK_SUSPECTED, ErrorState.PAD_FAIL, "gate_pad_fail");
                break;
            case LIVENESS_NOT_ESTABLISHED:
                finish(Verdict.Tier.LIMITED, ErrorState.ACTIVE_REQUIRED, "active_disabled");
                break;
            case CANNOT_JUDGE:
                finish(Verdict.Tier.CANNOT_JUDGE, ErrorState.POOR_QUALITY, "cannot_judge");
                break;
            default:
                startActive(f, g, v1, v2, v4, v5);
        }
    }

    // ------------------------------------------------------------------ active

    private void startActive(Frame f, DecisionGate.Decision g, TextureCnnVoter v1, MoireVoter v2, FrameEdgeVoter v4,
                             ReflectionVoter v5) {
        List<ChallengeType> pool = new ArrayList<>();
        for (String t : engine.config.value("active.challengeTypes", name()).split(",")) {
            pool.add(ChallengeType.valueOf(t.trim()));
        }
        long timeout = Long.parseLong(engine.config.value("active.challengeTimeoutMs", name()));
        ChallengeEngine ce = new ChallengeEngine(new ChallengePlanner(BLINK_MIN_FPS, 3.0, new SecureRandom()), pool,
                policy.minChallenges(), timeout);
        challenge = ce.newAttempt(guard.measuredFps());
        if (challenge == null) {
            finish(Verdict.Tier.LIMITED, ErrorState.ACTIVE_REQUIRED, "no_challenge_possible_at_this_fps");
            return;
        }
        String strict = null;
        if (g.strict()) {
            for (Map.Entry<String, Vote> e : passiveVotes.entrySet()) {
                if (e.getValue().label() == Vote.Label.ATTACK) {
                    strict = e.getKey();
                }
            }
        }
        List<PadVoter> voters = new ArrayList<>();
        voters.add(v1);
        voters.add(v2);
        voters.add(imageVoter("V4_frame_edge", v4::vote));
        voters.add(imageVoter("V5_reflection", v5::vote));
        reevaluator = new ChallengeReevaluator(voters, new HashSet<>(Collections.singleton(v1.name())), 8, strict);
        baseline = ActionDetectors.Baseline.of(new ArrayList<>(usableFeatures));
        state = State.ACTIVE;
        nextPrompt(f);
    }

    private void nextPrompt(Frame f) {
        ChallengeType c = challenge.current();
        currentChallenge = c;
        if (c != null) {
            detector = ActionDetectors.create(c, baseline, thresholds);
            detectorFor = c;
            if (st.autoPrompt) {
                challenge.prompted(f.arrivalNanos(), f.sequence());
            }
            Map<String, String> a = new LinkedHashMap<>();
            a.put("challenge", c.name());
            audit(LivenessAuditEvent.REG_LIVENESS_CHALLENGE_PROMPTED, a);
        }
    }

    private void runActive(Frame f, FrameFeatures ff) {
        boolean detected = detector != null && detector.update(ff);
        ChallengeSession.Outcome o = challenge.onFrame(f.sequence(), f.arrivalNanos(), detected);
        LoadShedder.Plan plan = shedder.plan(queue.size(), avgProcessingMs,
                guard.measuredFps() > 0 ? 1000.0 / guard.measuredFps() : 100.0, true);
        boolean runCnn = plan.cnnEveryNth() > 0 && processed % plan.cnnEveryNth() == 0;
        if (reevaluator.onFrame(f, runCnn) == ChallengeReevaluator.Status.PAD_FAIL) {
            finish(Verdict.Tier.ATTACK_SUSPECTED, ErrorState.PAD_FAIL, "attack_during_challenge");
            return;
        }
        if (o == ChallengeSession.Outcome.CHALLENGE_TIMEOUT) {
            finish(Verdict.Tier.ACTIVE_REQUIRED, ErrorState.CHALLENGE_TIMEOUT, "challenge_timeout");
        } else if (o == ChallengeSession.Outcome.ACTIVE_PASS) {
            ChallengeReevaluator.Finish fin = reevaluator.finish();
            if (fin == ChallengeReevaluator.Finish.OK) {
                binding.liveVerdictAt(System.nanoTime());
                finish(Verdict.Tier.LIVE_ACTIVE, null, "active_pass");
            } else if (fin == ChallengeReevaluator.Finish.PAD_FAIL) {
                finish(Verdict.Tier.ATTACK_SUSPECTED, ErrorState.PAD_FAIL, "attack_during_challenge");
            } else {
                finish(Verdict.Tier.ATTACK_SUSPECTED, ErrorState.CHALLENGE_FAILED, "strict_voter_not_cleared");
            }
        } else if (challenge.current() != detectorFor) {
            nextPrompt(f);
        }
    }

    /** A voter that works on one decoded frame, wrapped so the re-evaluation can run it on a window. */
    private PadVoter imageVoter(String name, BiFunction<PixelImage, Detection, Vote> fn) {
        return new PadVoter() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Vote vote(List<Frame> window) {
                try {
                    Frame last = window.get(window.size() - 1);
                    PixelImage img = analysis.image(last);
                    if (img == null) {
                        return new Vote(Vote.Label.ABSTAIN, 0.0, "undecodable_frame");
                    }
                    List<Detection> faces = analysis.faces(last);
                    return faces.size() == 1 ? fn.apply(img, faces.get(0)) : new Vote(Vote.Label.ABSTAIN, 0.0, "not_one_face");
                } catch (Exception e) {
                    return new Vote(Vote.Label.ABSTAIN, 0.0, "engine_error");
                }
            }
        };
    }

    // ------------------------------------------------------------------ end

    private void finish(Verdict.Tier tier, ErrorState error, String reason) {
        if (state == State.DONE || state == State.ABORTED) {
            return;
        }
        verdict = new Verdict(tier, error, reason);
        state = State.DONE;
        currentChallenge = null;
        long ms = System.currentTimeMillis() - startedAtMillis;
        engine.metrics.recordAttempt(workflow, ms, tier == Verdict.Tier.LIVE_PASSIVE || tier == Verdict.Tier.LIVE_ACTIVE, false);
        Map<String, String> a = new LinkedHashMap<>();
        a.put("tier", tier.name());
        if (error != null) {
            a.put("error", error.name());
        }
        audit(LivenessAuditEvent.REG_LIVENESS_VERDICT, a);
        running = false;
    }

    private void audit(LivenessAuditEvent e, Map<String, String> attrs) {
        try {
            engine.audit.append(e, workflow, attrs);
        } catch (RuntimeException ignored) {
            // the audit log refuses unsafe attributes; a refusal must never break the session
        }
    }

    /** For the Bench: the passive votes of the last passive run, by voter name. */
    public Map<String, Vote> passiveVotes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(passiveVotes));
    }

    /** For tests: the worker has ended and released its frames. */
    public boolean released() {
        return ended && usableFrames.isEmpty() && lastImage == null;
    }
}
