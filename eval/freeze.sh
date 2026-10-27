#!/bin/sh
# #62: freeze the thresholds. Writes eval/FROZEN = hash of the default configuration, engine settings, model files and the
# content hash of the engine sources (not the git commit: committing FROZEN must not change it) and a clean/dirty flag; a dirty
# tree cannot be frozen. Run it ONLY after DEV tuning is finished; any later change makes `run_eval --heldout` refuse.
# Usage: eval/freeze.sh [models-dir]
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$(cd "$HERE/.." && pwd)"; TOOLS="$ROOT/../tools-local"
if [ -d "$TOOLS/maven" ]; then
  export JAVA_HOME="${JAVA_HOME:-$(ls -d "$TOOLS"/jdk17/*/Contents/Home | head -1)}"
  export PATH="$(ls -d "$TOOLS"/maven/apache-maven-*/bin | head -1):$JAVA_HOME/bin:$PATH"
  : "${SAJEEV_MODELS:=$TOOLS/models}"
fi
MODELS="${1:-$SAJEEV_MODELS}"
[ -f "$HERE/target/cp.txt" ] || (cd "$ROOT" && mvn -q -o -Dmaven.repo.local="$TOOLS/m2" -pl eval -am -DskipTests package >&2)
exec java -cp "$HERE/target/classes:$(cat "$HERE/target/cp.txt")" io.sajeev.eval.FreezeHash "$MODELS" "$ROOT" "$HERE/FROZEN"
