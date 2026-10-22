#!/bin/sh
# #39: run the interop harness against the desktop MockMDS in three configurations and write docs/INTEROP_RESULTS.md.
# Needs: a Maven-packaged MockMDS build copy (see integrations/README.md) and the interop module built.
# Usage: ANDROID_JSON=<file> DESKTOP_JSON=<file> eval/rig/run_interop.sh <mockmds-build-dir> <java> <interop-classpath-file>
set -eu
M="$1"; JAVA="$2"; CPF="$3"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CP="$ROOT/interop/target/classes:$(cat "$CPF")"
REPLAY_DIR="$ROOT/testdata/clips-synthetic/fps15"
OUT="$ROOT/docs/INTEROP_RESULTS.md"
wait_free() { i=0; while (echo > /dev/tcp/127.0.0.1/4501) 2>/dev/null && [ $i -lt 40 ]; do sleep 1; i=$((i+1)); done; }
wait_ready() {  # discovery must answer with a device before the harness starts
  i=0
  while [ $i -lt 60 ]; do
    if curl -s -m 5 -X MOSIPDISC -H 'Content-Type: application/json' -d '{"type":"Biometric Device","modality":"Face"}' http://127.0.0.1:4501/device | grep -q deviceId; then return 0; fi
    sleep 2; i=$((i+1))
  done
  echo "mock did not become ready" >&2; exit 1
}
start() {
  pkill -f TestMockSBI 2>/dev/null || true; sleep 2; wait_free
  (cd "$M" && nohup "$JAVA" "$@" -cp "target/classes:$(cat cp.txt)" io.mosip.mock.sbi.test.TestMockSBI \
     "mosip.mock.sbi.device.purpose=Registration" "mosip.mock.sbi.biometric.type=Biometric Device" > ../mockmds-interop.log 2>&1 &)
  wait_ready; sleep 2
}
run() { "$JAVA" -cp "$CP" io.sajeev.interop.InteropMain "$1" "$2"; }
start
run /tmp/interop-1.md "live:MockMDS upstream behaviour (no replay properties)=127.0.0.1:4501:6" >/dev/null
start -Dsajeev.replay.dir="$REPLAY_DIR" -Dsajeev.replay.fps=15
run /tmp/interop-2.md "live:MockMDS fork, replay at 15 fps=127.0.0.1:4501:6" >/dev/null
start -Dsajeev.replay.dir="$REPLAY_DIR" -Dsajeev.replay.fps=2
run /tmp/interop-3.md "live:MockMDS fork, replay at 2 fps (below the PDF minimum)=127.0.0.1:4501:8" >/dev/null
pkill -f TestMockSBI 2>/dev/null || true
set -- "$OUT" /tmp/interop-1.md /tmp/interop-2.md /tmp/interop-3.md
[ "${ANDROID_JSON:-}" != "" ] && set -- "$@" "recorded:MockAndroidSBI (emulator)=$ANDROID_JSON"
[ "${DESKTOP_JSON:-}" != "" ] && set -- "$@" "recorded:MockMDS desktop (23 Oct)=$DESKTOP_JSON"
"$JAVA" -cp "$CP" io.sajeev.interop.MergeMain "$@"
echo "wrote $OUT"
