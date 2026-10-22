#!/bin/sh
# #47 Liveness Bench, minimal. Runs the real engine on a clip and prints verdict, votes and frame rate.
# Usage: bench/run.sh --clip <folder of .jpg | file.jpg | file.mjpeg> [--fps N] [--workflow RESIDENT] [--capture image.jpg]
# Needs a model directory: set SAJEEV_MODELS (or pass --models). Uses ../../tools-local for Java and Maven when it exists.
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
TOOLS="$ROOT/../tools-local"
if [ -d "$TOOLS/maven" ]; then
  export JAVA_HOME="${JAVA_HOME:-$(ls -d "$TOOLS"/jdk17/*/Contents/Home 2>/dev/null | head -1)}"
  export PATH="$(ls -d "$TOOLS"/maven/apache-maven-*/bin | head -1):$JAVA_HOME/bin:$PATH"
  MVN_REPO="-Dmaven.repo.local=$TOOLS/m2"
  : "${SAJEEV_MODELS:=$TOOLS/models}"; export SAJEEV_MODELS
else
  MVN_REPO=""
fi
CP="$HERE/target/cp.txt"
if [ ! -f "$HERE/target/classes/io/sajeev/bench/Bench.class" ] || [ ! -f "$CP" ]; then
  (cd "$ROOT" && mvn -q -o $MVN_REPO -pl bench -am -DskipTests package >&2)
fi
cd "$ROOT"   # clip paths are relative to the repo root unless absolute
exec java -cp "$HERE/target/classes:$(cat "$CP")" io.sajeev.bench.Bench "$@"
