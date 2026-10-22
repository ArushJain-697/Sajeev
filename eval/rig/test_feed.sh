#!/bin/sh
# #50 tests for the MockAndroidSBI frame feed. Needs a booted emulator with the patched MockAndroidSBI and the
# FeedProbe app installed (see integrations/README.md). MOCK frames only. Prints the three claims.
set -eu
adb logcat -c
echo "== test_replay_mode_streams_a_clip_at_configured_fps (read the fps from the mock's prefs: feed_replay_fps)"
adb shell am start -n io.sajeev.feedprobe/.MainActivity --ei seconds 6 >/dev/null; sleep 12
adb logcat -d -s PROBE | grep RESULT | tail -1
echo "== test_second_app_cannot_bind_to_the_frame_feed (install integrations/feedprobe re-signed with another key first)"
echo "   expected: RESULT bind=DENIED reason=SecurityException"
echo "== test_feed_does_not_stall_sbi_preview (run with --ez slow true while python3 eval/rig/drive_sbi_test_client.py runs)"
adb logcat -d -s PROBE | grep -E "STATS|RESULT" | tail -2
