#!/bin/sh
# #59 ingest tooling. Usage: eval/ingest.sh <session-id> <subject-id> <device-id> <raw-folder>
# Renames raw clips to session_subject_device_species_NNN.mp4, probes fps and duration with ffprobe,
# hashes each file, refuses unlabelled files, and writes eval/clips/<session>/manifest.csv.
set -eu
[ $# -eq 4 ] || { echo "usage: $0 <session-id> <subject-id> <device-id> <raw-folder>" >&2; exit 2; }
SESSION="$1"; SUBJECT="$2"; DEVICE="$3"; RAW="$4"
ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/clips/$SESSION"
case "$SESSION" in *[!A-Za-z0-9_-]*|"") echo "session id may use only letters, digits, _ and -" >&2; exit 2;; esac
[ -d "$RAW" ] || { echo "no such folder: $RAW" >&2; exit 2; }
mkdir -p "$OUT"
MAN="$OUT/manifest.csv"
[ -f "$MAN" ] || echo "file,sha256,session,subject,device,species,fps,duration_s" > "$MAN"
N=0; BAD=0
for F in "$RAW"/*; do
  [ -f "$F" ] || continue
  BASE="$(basename "$F")"
  case "$BASE" in *.mov|*.mp4|*.MOV|*.MP4) ;; *) continue;; esac
  FIRST="$(echo "$BASE" | sed 's/[_ -].*//')"
  case "$FIRST" in
    bona|P1|P2|P3|P4|P5|P6) SPECIES="$FIRST";;
    *) echo "REJECTED (no species label): $BASE" >&2; BAD=$((BAD+1)); continue;;
  esac
  N=$(( $(grep -c ",$SPECIES," "$MAN" || true) + 1 ))
  NEW="${SESSION}_${SUBJECT}_${DEVICE}_${SPECIES}_$(printf '%03d' "$N").mp4"
  [ ! -e "$OUT/$NEW" ] || { echo "exists, not overwritten: $NEW" >&2; BAD=$((BAD+1)); continue; }
  ffmpeg -hide_banner -loglevel error -i "$F" -c:v libx264 -an -pix_fmt yuv420p "$OUT/$NEW"
  FPS="$(ffprobe -v error -select_streams v:0 -show_entries stream=avg_frame_rate -of csv=p=0 "$OUT/$NEW")"
  DUR="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT/$NEW")"
  SHA="$(shasum -a 256 "$OUT/$NEW" | cut -d' ' -f1)"
  echo "$NEW,$SHA,$SESSION,$SUBJECT,$DEVICE,$SPECIES,$FPS,$DUR" >> "$MAN"
done
echo "ingested into $OUT; rejected: $BAD"
echo "per species:"; tail -n +2 "$MAN" | cut -d, -f6 | sort | uniq -c
