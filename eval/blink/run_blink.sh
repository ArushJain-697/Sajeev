#!/bin/sh
# #61: blink recall vs fps. Without an argument it uses SYNTHETIC blinks; with a folder of real EAR traces (time_ms,ear CSV,
# one blink per file, recorded at 30 fps or more) it uses those. Writes eval/blink/chosen_blink_fps.txt.
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$(cd "$HERE/../.." && pwd)"; TOOLS="$ROOT/../tools-local"
if [ -d "$TOOLS/maven" ]; then
  export JAVA_HOME="${JAVA_HOME:-$(ls -d "$TOOLS"/jdk17/*/Contents/Home | head -1)}"
  export PATH="$(ls -d "$TOOLS"/maven/apache-maven-*/bin | head -1):$JAVA_HOME/bin:$PATH"
fi
[ -f "$ROOT/eval/target/cp.txt" ] || (cd "$ROOT" && mvn -q -o -Dmaven.repo.local="$TOOLS/m2" -pl eval -am -DskipTests package >&2)
java -cp "$ROOT/eval/target/classes:$(cat "$ROOT/eval/target/cp.txt")" io.sajeev.eval.BlinkRecall "$@" | tee "$HERE/chosen_blink_fps.txt"
