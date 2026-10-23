#!/bin/sh
# #39 part (b): the engine fed from MockMDS through the SBI adapter, at 15 fps and at 2 fps (below the PDF minimum).
# Usage: SAJEEV_MODELS=<dir> eval/rig/run_engine_via_device.sh <mockmds-build-dir> <java> <maven-local-repo>
# (the mock build copy is the same one run_interop.sh uses; needs mvn on the PATH)
set -eu
M="$1"; JAVA="$2"; REPO="$3"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FRAMES="$(mktemp -d)"; i=0
while [ $i -lt 60 ]; do cp "$ROOT/testdata/fixtures-faces/one_face.jpg" "$FRAMES/$(printf %03d $i).jpg"; i=$((i+1)); done
start() {
  pkill -f TestMockSBI 2>/dev/null || true; sleep 2
  (cd "$M" && nohup "$JAVA" -Dsajeev.replay.dir="$FRAMES" -Dsajeev.replay.fps="$1" -cp "target/classes:$(cat cp.txt)" io.mosip.mock.sbi.test.TestMockSBI \
     "mosip.mock.sbi.device.purpose=Registration" "mosip.mock.sbi.biometric.type=Biometric Device" > "$FRAMES/mock.log" 2>&1 &)
  n=0
  until curl -s -m 5 -X MOSIPDISC -H 'Content-Type: application/json' -d '{"type":"Biometric Device","modality":"Face"}' http://127.0.0.1:4501/device | grep -q deviceId; do
    sleep 2; n=$((n+1)); [ $n -lt 40 ] || { echo "mock not ready" >&2; exit 1; }
  done
}
for CASE in "15:NOT_LIVE" "2:BELOW_FPS"; do
  FPS="${CASE%%:*}"; EXP="${CASE##*:}"
  start "$FPS"
  echo "== replay at $FPS fps, expecting $EXP"
  (cd "$ROOT" && mvn -o -Dmaven.repo.local="$REPO" -pl devices test -Dtest=EngineThroughDeviceTest -Dsajeev.device.expect="$EXP" \
      -Dsajeev.models="$SAJEEV_MODELS" 2>&1 | grep -E "DEVICE RUN|Tests run:.*[0-9]$|FAIL|expected" || true)
done
pkill -f TestMockSBI 2>/dev/null || true
