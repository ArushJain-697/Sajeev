#!/bin/sh
# #60 run_eval: run the real engine on a manifest of clips and write metrics JSON.
# Usage: eval/run_eval.sh --manifest eval/clips/<session>/manifest.csv --clips eval/clips/<session> --out metrics.json [--models dir]
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$(cd "$HERE/.." && pwd)"; TOOLS="$ROOT/../tools-local"
if [ -d "$TOOLS/maven" ]; then
  export JAVA_HOME="${JAVA_HOME:-$(ls -d "$TOOLS"/jdk17/*/Contents/Home | head -1)}"
  export PATH="$(ls -d "$TOOLS"/maven/apache-maven-*/bin | head -1):$JAVA_HOME/bin:$PATH"
  MVN_REPO="-Dmaven.repo.local=$TOOLS/m2"; : "${SAJEEV_MODELS:=$TOOLS/models}"; export SAJEEV_MODELS
else MVN_REPO=""; fi
[ -f "$HERE/target/cp.txt" ] || (cd "$ROOT" && mvn -q -o $MVN_REPO -pl eval -am -DskipTests package >&2)
exec java -cp "$HERE/target/classes:$(cat "$HERE/target/cp.txt")" io.sajeev.eval.RunEval "$@"
