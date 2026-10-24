# Model and configuration updates (PDF: "synchronised securely when the client is online")

**Rule:** the liveness engine never uses the network. Updates reach it in two steps that are kept apart on purpose.

1. **Delivery (client's own sync, needs connectivity).** The Registration Client already syncs its configuration (global parameters) from the server; our liveness keys (`mosip.registration.liveness.*`) arrive that way, with client-side defaults when the server sends nothing. A new model set is delivered by the same kind of sync into a *staging folder* that contains `manifest.txt`, `manifest.sig` and the model files. Which download call the client uses is the client's business; we did not build or test a server endpoint (there is no MOSIP server in our test setup).
2. **Installation (offline, in this repo).** `ModelStore.install(staging)` verifies the staged update completely before anything changes: the manifest signature (ECDSA P-256, bundled trust root), every file's size and SHA-256 (including `.onnx.data` sidecars), and a manifest version strictly newer than the one in use. It copies the update into `<root>/v<N>`, verifies the copy again, and only then switches `<root>/CURRENT` with an atomic rename. Any failure leaves the old version in use and removes the half-copied folder. The last good version is kept; `rollback()` goes back to it.

**Making a signed update (release side):**
`java -cp liveness-core/target/classes io.sajeev.model.ManifestTool sign <models> <version> <private.der> <models>/manifest.txt <models>/manifest.sig` (the version must be higher than the one in use; the private key stays outside git).

**Loading:** `SajeevLivenessProvider` (desktop) uses `<model dir>/CURRENT` when the folder is a store, otherwise the folder itself; either way the models load only through `EngineModels.loadVerified`. A new version is picked up the next time the engine starts.

**Tested:** 6 tests in `ModelStoreTest` (first install, update, a tampered update keeps the old version and leaves no half folder, downgrade and same-version replay are refused, a wrong key or missing signature is refused, rollback and pruning). **Not tested:** a real download from a server, and the Android app using the store layout (it still reads a plain folder).
