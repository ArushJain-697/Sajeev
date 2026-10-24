#!/bin/sh
# Downloads the model files (about 40 MB) into liveness-models/models and checks them.
# The models are not in git (see liveness-models/README.md for their licences). Two checks run:
#   1. the zip's SHA-256 is the one written below;
#   2. the engine itself refuses any model whose hash does not match the signed manifest.txt/manifest.sig inside the zip.
# Usage: scripts/fetch_models.sh            (then: mvn test -Dsajeev.models=liveness-models/models)
# Override the source with MODELS_URL=<url or file:// path> (for an offline copy).
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
URL="${MODELS_URL:-https://github.com/ArushJain-697/Sajeev/releases/download/models-0.1.0/sajeev-models-0.1.0.zip}"
SHA256="80bc296ac8876b37386ef3b30dfacffaa329861eba395d157a30956381d099f6"
DEST="$ROOT/liveness-models/models"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
echo "Downloading $URL"
curl -fsSL "$URL" -o "$TMP/models.zip"
if command -v sha256sum >/dev/null 2>&1; then GOT="$(sha256sum "$TMP/models.zip" | cut -d' ' -f1)"; else GOT="$(shasum -a 256 "$TMP/models.zip" | cut -d' ' -f1)"; fi
if [ "$GOT" != "$SHA256" ]; then echo "CHECKSUM MISMATCH: expected $SHA256 got $GOT" >&2; exit 1; fi
rm -rf "$DEST"; mkdir -p "$DEST"
unzip -q "$TMP/models.zip" -d "$DEST"
echo "Models are in $DEST"
echo "Run the tests with:  mvn test -Dsajeev.models=$DEST"
