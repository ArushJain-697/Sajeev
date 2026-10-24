#!/bin/sh
# Builds ready-to-push branches for the four MOSIP forks by applying our patches to fresh clones at the pinned commits.
# Run on Arush's Mac (needs the clones in ../upstream and the build copies in ../tools-local/build). Nothing is pushed.
# Usage: integrations/make_fork_branches.sh <output-dir>
# Then, per repo: cd <output-dir>/<repo>; git remote add fork https://github.com/<you>/<repo>.git; git push fork <branch>
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; P="$ROOT/integrations/patches"; U="$ROOT/../upstream"; B="$ROOT/../tools-local/build"
OUT="$1"; mkdir -p "$OUT"
NAME="Arush Jain"; MAIL="thearushjain3321@gmail.com"
commit() { git -c user.name="$NAME" -c user.email="$MAIL" commit -q -m "$1" -m "Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"; }
apply() { # apply <patch> <strip> <message>
  git apply --whitespace=nowarn -p"$2" "$P/$1" && git add -A && commit "$3"; }

echo "== registration-client (release-1.2.0.x, JDK 11)"
git clone -q --local --no-hardlinks "$B/registration-client-1.2.0.x" "$OUT/registration-client" && cd "$OUT/registration-client"
git checkout -q c1cc4e7fc1625ccc6ef26282910934bfcb9d164c && git checkout -q -b sajeev/release-1.2.0.x
apply 0001-registration-api-liveness-spi.patch 1 "Add the liveness SPI and a fail-closed stub (SAJEEV 0001)"
apply 0005-resident-capture-liveness-wiring.patch 1 "Liveness in resident capture, operator and supervisor face authentication, config, messages, audit (SAJEEV 0005)"
echo "   branch sajeev/release-1.2.0.x ready"

echo "== android-registration-client (Flutter 3.10.x)"
git clone -q --local --no-hardlinks "$U/android-registration-client" "$OUT/android-registration-client" && cd "$OUT/android-registration-client"
git checkout -q 6e1943c && git checkout -q -b sajeev/liveness
apply 0006-arc-liveness-pigeon-bridge.patch 1 "Liveness bridge, widget, resident gate, face login and approval (SAJEEV 0006)"
apply 0007-arc-liveness-config-i18n-audit.patch 1 "Liveness settings, six-language texts, audit IDs (SAJEEV 0007)"
echo "   branch sajeev/liveness ready (the engine AAR is built from this repo: integrations/engine-android, then copied to android/app/libs/sajeev-engine.aar)"

echo "== android-camera-mds (MockAndroidSBI)"
git clone -q --local --no-hardlinks "$B/android-camera-mds" "$OUT/android-camera-mds" && cd "$OUT/android-camera-mds"
git checkout -q da9d7e49edae2a94cccddf948b7e7633360cd837 && git checkout -q -b sajeev/frame-feed
apply 0002-sbitestclient-for-active-mock.patch 1 "SBITestClient talks to the active mock (SAJEEV 0002)"
apply 0003-mockandroidsbi-frame-feed.patch 1 "MockAndroidSBI frame feed service, replay and mock flag (SAJEEV 0003)"
echo "   branch sajeev/frame-feed ready"

echo "== mosip-mock-services (MockMDS)"
git clone -q --local --no-hardlinks "$U/mosip-mock-services" "$OUT/mosip-mock-services" && cd "$OUT/mosip-mock-services"
git checkout -q 3d2174d && git checkout -q -b sajeev/replay-fps
apply 0004-mockmds-replay-fps-mockflag.patch 1 "MockMDS frame replay, configurable fps and mock flag (SAJEEV 0004)"
echo "   branch sajeev/replay-fps ready"
echo "Nothing was pushed. Add your fork as a remote in each folder and push the branch."
