# Build log

<!-- Honest timestamps. One line per ticket when done and verified. -->

## #13 liveness-api freeze (22 Oct 2026)
- Added the frozen public types listed in `docs/API.md`. Java 11 only.
- Tests: `ApiDependencyTest` (no core/UI/ONNX/Android imports), `FrameAndVoteTest` (6 claims). Plus the #01 `SkeletonTest`. Result: 8 tests, 0 failures (JDK 21, `--release 11`).
- Interpretation of the spec: `SessionState` was folded into `LivenessSession.State`. A `Decision`/`Tier` enum split lives in `GateResult` and `Verdict`.
