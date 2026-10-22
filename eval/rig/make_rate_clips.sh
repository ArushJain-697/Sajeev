#!/bin/sh
# #11 (P0-11) recording rig proof, synthetic part. Makes a 10 s 15 fps test clip and re-samples it to 3, 10 and 15 fps.
# SYNTHETIC: the source is ffmpeg's test pattern, not a person. The camera part needs Arush (see BUILD_LOG #11).
# Output: testdata/clips-synthetic/fps<N>/NNN.jpg  (FileAdapter replays these folders).
set -e
OUT="$(cd "$(dirname "$0")/../.." && pwd)/testdata/clips-synthetic"
TMP="${TMPDIR:-/tmp}/sajeev-rig-$$"
mkdir -p "$TMP" "$OUT"
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "testsrc=size=320x240:rate=15" -t 10 -c:v libx264 -pix_fmt yuv420p "$TMP/base15.mp4"
for FPS in 3 10 15; do
  rm -rf "$OUT/fps$FPS"; mkdir -p "$OUT/fps$FPS"
  ffmpeg -hide_banner -loglevel error -y -i "$TMP/base15.mp4" -vf "fps=$FPS" -q:v 4 "$OUT/fps$FPS/%03d.jpg"
  COUNT=$(ls "$OUT/fps$FPS" | wc -l | tr -d ' ')
  echo "fps$FPS frames=$COUNT"
done
rm -rf "$TMP"
