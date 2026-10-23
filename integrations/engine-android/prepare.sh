#!/bin/sh
# #48: copy the engine jars, the models and a test photo into the Android project. Run after `mvn package` in sajeev/.
set -eu
H="$(cd "$(dirname "$0")" && pwd)"; R="$H/../.."; T="$R/../tools-local"
cp "$R/liveness-api/target/liveness-api-0.1.0-SNAPSHOT.jar" "$R/liveness-core/target/liveness-core-0.1.0-SNAPSHOT.jar" "$H/engine/libs/"
A="$H/engine/src/androidTest/assets"; mkdir -p "$A/models"
for d in yunet landmark minifasnet sface; do rm -rf "$A/models/$d"; cp -R "$T/models/$d" "$A/models/$d"; done
cp "$T/models/manifest.txt" "$T/models/manifest.sig" "$A/models/"
cp "$R/testdata/fixtures-faces/one_face.jpg" "$A/"
