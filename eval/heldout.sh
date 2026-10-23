#!/usr/bin/env bash
# heldout.sh — seal, unseal, verify and leak-check the HELD-OUT recordings.   (BUILD_MAP #62, HELDOUT_PROTOCOL.md)
#
# Why: five parallel sessions must not read or tune on the held-out set (RED_TEAM R3-11).
# HONEST LIMIT: every session runs as the same OS user, so `chmod`/`chflags` is an
# *accidental-access guard* (a tool that tries to read the files gets "permission denied"),
# not a security boundary. The real protection is the hash seal + the access log: any unseal
# is recorded, and the evaluation refuses to run if the hashes changed or the log looks wrong.
#
# Usage:
#   heldout.sh seal                       # hash everything, write HELDOUT.sha256, lock the directory
#   heldout.sh status                     # sealed? frozen? how many access-log entries?
#   SAJEEV_ROLE=proof heldout.sh unseal "reason"   # only the proof session; needs eval/FROZEN
#   heldout.sh verify                     # recompute hashes, compare with HELDOUT.sha256 (dir must be unsealed)
#   heldout.sh leakcheck <dev_dir>        # fail if any held-out file is byte-identical to a DEV file
#   heldout.sh reseal                     # lock again after the run
#
# Environment (defaults in brackets):
#   SAJEEV_EVAL_DIR  [./eval]   — contains data/heldout, HELDOUT.sha256, FROZEN, HELDOUT_ACCESS.log
set -euo pipefail

EVAL="${SAJEEV_EVAL_DIR:-./eval}"
DIR="$EVAL/data/heldout"
SHA="$EVAL/HELDOUT.sha256"
FROZEN="$EVAL/FROZEN"
LOG="$EVAL/HELDOUT_ACCESS.log"

now() { date -u +%Y-%m-%dT%H:%M:%SZ; }
log() { mkdir -p "$EVAL"; printf '%s  %s  user=%s  %s\n' "$(now)" "$1" "$(id -un)" "${2:-}" >> "$LOG"; }
die() { echo "heldout.sh: $*" >&2; exit 1; }
hash_file() { shasum -a 256 "$1" | awk '{print $1}'; }

# The held-out set is a FLAT directory of files (names come from eval/ingest.sh), so locking is
# two steps: files first, then the directory itself. Unlocking is the reverse.
# (`chmod -R a-rwx` would strip the directory first and then fail to reach the files inside.)
HAVE_CHFLAGS=0; command -v chflags >/dev/null 2>&1 && HAVE_CHFLAGS=1
lock_dir() {
  find "$DIR" -mindepth 1 -maxdepth 1 -type f -exec chmod a-rwx {} +
  [ "$HAVE_CHFLAGS" = 1 ] && find "$DIR" -mindepth 1 -maxdepth 1 -type f -exec chflags uchg {} + || true
  chmod a-rwx "$DIR"
  [ "$HAVE_CHFLAGS" = 1 ] && chflags uchg "$DIR" || true
}
unlock_dir() {
  [ "$HAVE_CHFLAGS" = 1 ] && chflags nouchg "$DIR" || true
  chmod u+rwx "$DIR"
  [ "$HAVE_CHFLAGS" = 1 ] && find "$DIR" -mindepth 1 -maxdepth 1 -type f -exec chflags nouchg {} + || true
  find "$DIR" -mindepth 1 -maxdepth 1 -type f -exec chmod u+rw {} +
}
is_locked() { [ -d "$DIR" ] && [ ! -r "$DIR" ]; }

compute_listing() {   # prints "<sha256>  <relative path>" for every file, sorted by path
  ( cd "$DIR" && find . -type f ! -name '.DS_Store' -print0 | LC_ALL=C sort -z \
      | while IFS= read -r -d '' f; do printf '%s  %s\n' "$(hash_file "$f")" "${f#./}"; done )
}

cmd="${1:-}"; shift || true
case "$cmd" in
  seal)
    [ -d "$DIR" ] || die "no directory $DIR"
    [ -e "$SHA" ] && die "already sealed ($SHA exists). Use 'unseal' / 'reseal' via the proof session."
    [ -z "$(find "$DIR" -mindepth 1 -type d)" ] || die "held-out must be a flat directory of files (no sub-directories)"
    n=$(find "$DIR" -type f ! -name '.DS_Store' | wc -l | tr -d ' ')
    [ "$n" -gt 0 ] || die "no files in $DIR"
    listing="$(compute_listing)"
    root="$(printf '%s\n' "$listing" | shasum -a 256 | awk '{print $1}')"
    { printf '%s\n' "$listing"; echo "FILES $n"; echo "ROOT $root"; echo "SEALED_AT $(now)"; } > "$SHA"
    log SEALED "files=$n root=$root"
    lock_dir
    echo "sealed $n files; root=$root"
    ;;
  status)
    if [ -e "$SHA" ]; then echo "sealed:   yes ($(grep '^SEALED_AT' "$SHA" | cut -d' ' -f2))"; else echo "sealed:   no"; fi
    if is_locked; then echo "locked:   yes (directory unreadable)"; else echo "locked:   no"; fi
    if [ -e "$FROZEN" ]; then echo "frozen:   yes (thresholds config hash recorded)"; else echo "frozen:   no (eval/FROZEN missing)"; fi
    if [ -e "$LOG" ]; then echo "access log entries: $(wc -l < "$LOG" | tr -d ' ')"; else echo "access log entries: 0"; fi
    ;;
  unseal)
    reason="${1:-}"
    [ -n "$reason" ] || die "a reason is required: heldout.sh unseal \"final held-out run\""
    [ "${SAJEEV_ROLE:-}" = "proof" ] || die "only the proof session may unseal (set SAJEEV_ROLE=proof)"
    [ -e "$SHA" ]    || die "not sealed"
    [ -e "$FROZEN" ] || die "refusing: $FROZEN is missing — thresholds must be frozen before the held-out set is opened"
    unlock_dir
    log UNSEALED "reason=\"$reason\""
    echo "unsealed (logged). Run 'verify' next, then 'reseal' when done."
    ;;
  verify)
    [ -e "$SHA" ] || die "not sealed"
    is_locked && die "directory is locked; unseal first"
    expect="$(grep -v -E '^(FILES|ROOT|SEALED_AT) ' "$SHA")"
    actual="$(compute_listing)"
    if [ "$expect" = "$actual" ]; then echo "verify: OK (hashes match the seal)"; log VERIFIED ok; else
      log VERIFY_FAILED "hash mismatch"; diff <(printf '%s\n' "$expect") <(printf '%s\n' "$actual") || true
      die "verify: FAILED — the held-out set changed after sealing"; fi
    ;;
  leakcheck)
    dev="${1:-}"; [ -d "$dev" ] || die "usage: heldout.sh leakcheck <dev_dir>"
    [ -e "$SHA" ] || die "not sealed"
    # compares against the *sealed hashes*, so it works while the held-out directory is still locked
    held="$(grep -v -E '^(FILES|ROOT|SEALED_AT) ' "$SHA" | awk '{print $1}' | LC_ALL=C sort -u)"
    dev_hashes="$(find "$dev" -type f ! -name '.DS_Store' -print0 | while IFS= read -r -d '' f; do hash_file "$f"; done | LC_ALL=C sort -u)"
    common="$(comm -12 <(printf '%s\n' "$held") <(printf '%s\n' "$dev_hashes") || true)"
    if [ -z "$common" ]; then echo "leakcheck: OK (no held-out file is identical to a DEV file)"; else
      echo "leakcheck: FAILED — identical files in DEV and HELD-OUT:" >&2; printf '%s\n' "$common" >&2; exit 1; fi
    ;;
  reseal)
    [ -e "$SHA" ] || die "not sealed"
    is_locked && die "already locked"
    lock_dir; log RESEALED ""; echo "locked again"
    ;;
  *)
    sed -n '2,25p' "$0"; exit 2 ;;
esac
