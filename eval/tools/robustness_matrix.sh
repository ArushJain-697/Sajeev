#!/bin/sh
# Robustness matrix: every real clip at 2 resolutions x 4 frame rates through ingest + run_eval (RESIDENT, current defaults).
S="$1"; R="$(cd "$(dirname "$0")/../.." && pwd)"  # usage: eval/tools/robustness_matrix.sh <workdir with src/*.mp4>
cd "$R" || exit 1
run_variant() {
  res=$1; fps=$2; V=${res}_${fps}fps; raw=$S/raw_$V; rm -rf "$raw"; mkdir -p "$raw"
  if [ "$res" = vga ]; then VF="crop=1440:1080,scale=640:480,fps=$fps"; else VF="fps=$fps"; fi
  for f in "$S"/src/*.mp4; do ffmpeg -v error -y -i "$f" -vf "$VF" -an -c:v libx264 -crf 18 "$raw/$(basename "$f")"; done
  rm -rf "eval/clips/rb_$V"; sh eval/ingest.sh "rb_$V" s1 macbook-webcam "$raw" > "$S/ingest_$V.log" 2>&1
  sh eval/run_eval.sh --manifest "eval/clips/rb_$V/manifest.csv" --clips "eval/clips/rb_$V" --out "$S/m_$V.json" > "$S/eval_$V.log" 2>&1
  echo "DONE $V" >> "$S/matrix_done.txt"
}
: > "$S/matrix_done.txt"
for res in hd vga; do
  run_variant $res 30 & run_variant $res 15 & run_variant $res 10 & run_variant $res 5 &
  wait
done
echo ALLDONE >> "$S/matrix_done.txt"
