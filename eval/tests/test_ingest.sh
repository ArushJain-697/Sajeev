#!/bin/sh
# #59 tests. Claims: test_manifest_lists_every_file_with_hash_and_species, test_ingest_rejects_files_without_species_label
set -eu
HERE="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
SESSION="unit-test-$$"
mkdir -p "$TMP/raw"
ffmpeg -hide_banner -loglevel error -f lavfi -i testsrc=size=160x120:rate=5 -t 1 -c:v libx264 "$TMP/raw/bona_glasses_off_01.mov"
ffmpeg -hide_banner -loglevel error -f lavfi -i testsrc=size=160x120:rate=5 -t 1 -c:v libx264 "$TMP/raw/P1_glossy_01.mov"
ffmpeg -hide_banner -loglevel error -f lavfi -i testsrc=size=160x120:rate=5 -t 1 -c:v libx264 "$TMP/raw/mystery_clip.mov"
# claim 2: unlabelled file is rejected and does not appear in the manifest
"$HERE/ingest.sh" "$SESSION" s01 dev1 "$TMP/raw" > "$TMP/out.txt" 2>&1
grep -q "REJECTED (no species label): mystery_clip.mov" "$TMP/out.txt" || { echo "FAIL reject"; exit 1; }
! grep -q "mystery" "$HERE/clips/$SESSION/manifest.csv" || { echo "FAIL manifest has rejected file"; exit 1; }
# claim 1: every accepted file has hash, species and fps in the manifest
LINES="$(tail -n +2 "$HERE/clips/$SESSION/manifest.csv" | wc -l | tr -d ' ')"
[ "$LINES" = "2" ] || { echo "FAIL expected 2 rows, got $LINES"; exit 1; }
tail -n +2 "$HERE/clips/$SESSION/manifest.csv" | while IFS=, read -r f sha sess subj dev sp fps dur; do
  [ -n "$sha" ] && [ -n "$sp" ] && [ -n "$fps" ] || { echo "FAIL incomplete row: $f"; exit 1; }
done
rm -rf "$HERE/clips/$SESSION"
echo "PASS: both claims hold"
