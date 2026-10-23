# Security and privacy

What the code does today (23 Oct 2026), and what it does not claim.

## Data handling

- **Frames** live in memory only: a bounded queue of at most 8 frames and the last 8 usable frames of the window. After a verdict, abort or close the worker drops frames, images and landmarks (test `test_session_close_releases_all_frames`). Nothing is written to disk by the engine.
- **Binding evidence** is a few face embeddings (SFace, 128 numbers) and landmark ratios kept in memory for the session; they are not stored, logged or exported.
- **The captured face** (ISO 19794-5 with JPEG 2000) is converted to a PNG in memory for the Binding check and discarded. Storage of the biometric stays with the existing registration packet code.
- **Audit log** (`REG-LIV-001..011`): append-only with a hash chain (an edit or deletion is detected). It refuses keys such as image, frame, embedding, biometric and user id, and values that are long or look base64. Scores are logged as bands (LOW, MID, HIGH), not raw values. The client writes its own audit events through `AuditManagerService` with the same rule: workflow and outcome only.
- **Metrics** have no method that takes a string, so no identifier can be passed in.
- **Diagnostic mode** is off by default, shown only to the operator when an admin enables it, carries a persistent DIAGNOSTIC MODE banner, and is always empty for packets.
- **Retry counter** belongs to the session, not the process. An attempt oracle alert (5 PAD failures from one workstation within 10 minutes, TARGET) uses a hashed workstation key.

## Integrity and fail-closed behaviour

- **Models** are listed in a signed manifest (ECDSA P-256 over the whole manifest, SHA-256 per model); a tampered or downgraded model is refused and the last good version is kept. Updates only arrive through the online sync and are verified offline at load. (Ed25519 was the plan; it is not in JDK 11, decision D43.) The desktop bridge and the Android controller now load models only through `EngineModels.loadVerified` (signed manifest, trust root bundled in the engine jar); the dev key's private half is outside the repository, and switching verification off on the desktop needs the explicit property `mosip.registration.liveness.model.verify=false` (logged). The bench, eval and test code load models unverified on purpose (developer tools).
- **Fail closed:** no engine, a failed model load, a thrown error, a missing session or a null result is `UNAVAILABLE`/`ENGINE_ERROR` and never a pass. Disabling liveness is an explicit, audited configuration flag; a missing or invalid value keeps liveness on.
- **No network:** no class under `liveness-core/src/main` refers to a network API (static test). A Docker `--network none` run is prepared (`eval/ci/run_no_network.sh`) but was not executed.
- **Order of checks** makes a replayed capture useless on its own: Binding needs the live face seen in the same session within the capture window.
- **Android frame feed (mock):** a signature-level permission and a caller-package check protect the feed; not exported.

## Not claimed

- No claim of ISO/IEC 30107-3 conformance or lab certification; see `ISO30107_ALIGNMENT.md`.
- Resistance to deepfake or live puppeting is only partly addressed (re-evaluation during the challenge); it is a named limit.
- The MiniFASNet weights licence is not verified (see DISCLOSURES). All thresholds are TARGET values until tuned on recordings.
- The prompt text in French and Arabic is machine-translated and unreviewed.
- Operator and supervisor face checks show that a live person was present; who the person is stays the job of the existing face match.
