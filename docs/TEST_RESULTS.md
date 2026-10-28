# Test results

Run on 28 Oct 2026 on one Apple-silicon Mac (JDK 17, `--release 11`). The full list of test cases, one line per test with its claim as its name, is in [TEST_CASES.md](TEST_CASES.md). Every number on this page was produced by a command shown on this page; none is typed by hand from memory.

## 1. Automated tests

| Part | Tests | Passed | Failed | Skipped | How it was run |
|---|---|---|---|---|---|
| `liveness-api` | 8 | 8 | 0 | 0 | `mvn … test` below |
| `liveness-core` (engine) | 224 | 224 | 0 | 0 | same |
| `devices` (SBI adapters) | 14 | 9 | 0 | 5 | same; the 5 need a running MockMDS |
| `bench` | 5 | 5 | 0 | 0 | same |
| `eval` (evaluation harness) | 25 | 25 | 0 | 0 | same |
| **Engine total** | **276** | **271** | **0** | **5** | |
| Desktop client patches (liveness tests, JDK 11) | 39 | 38 | 0 | 1 | in the `release-1.2.0.x` build; `registration-client` compiles |
| Android client: Dart widget and logic tests | 33 | 33 | 0 | 0 | `flutter test test/liveness test/liveness_events_test.dart` |
| Android client: Java (clientmanager) | 12 | 12 | 0 | 0 | settings, lockout, local-override refusal |
| Android engine library: emulator tests | 7 | 7 | 0 | 0 | Android 13 arm64 emulator, 23 Oct (not re-run after 25 Oct) |

```
scripts/fetch_models.sh
mvn -pl liveness-api,liveness-core,devices,bench,eval test -Dsajeev.models=$PWD/liveness-models/models
```

**Edge cases the tests cover** (a sample; each is a test name): `test_abstain_never_counts_as_live`, `test_two_attack_votes_always_pad_fail`, `test_a_stream_of_one_repeated_frame_is_a_frozen_stream_and_never_passes`, `test_a_stream_with_sensor_noise_is_not_called_frozen`, `test_a_session_fed_huge_declared_frames_ends_without_a_live_verdict_and_does_not_hang`, `test_no_verdict_time_and_a_huge_timeout_cannot_overflow_into_a_pass`, `test_stream_stalled_after_2s_without_frames`, `test_timeout_reports_challenge_timeout`, `test_two_faces_pauses_and_reports_multiple`, `test_dark_frame_reports_poor_quality_with_reason`, `test_live_then_swap_is_rejected`, `test_two_faces_in_capture_is_rejected`, `test_mutation_removing_binding_accepts_the_swap`, `test_stillness_is_never_called_an_attack`, `test_metrics_json_is_reproducible_byte_for_byte`, `test_heldout_run_refuses_without_frozen_file`. The safety laws (no orphan capture, less evidence never helps, veto wins) are property tests with 10,000 generated cases each.

## 2. Real recordings

**Limit first:** one person, one room, one Mac webcam. 2 bona fide clips and 4 attack clips (a phone showing a photo once, a phone playing a video three times). No printed-photo attack was recorded. These show how the system behaves, not error rates: no APCER or BPCER is claimed.

**Robustness matrix** (`eval/tools/robustness_matrix.sh`): the bona fide clips cut into 8 presentations of 10 s; every clip re-encoded at 1620x1080 and 640x480, each at 30, 15, 10 and 5 fps; run through `eval/run_eval.sh` with the resident defaults.

| Presented | Presentations | Accepted | Not accepted |
|---|---|---|---|
| Real person | 64 | 57 | 7: one clip (the person staring for 10 s) found no evidence of life and went to the challenge |
| Phone showing a photo | 8 | **0** | 8: no motion evidence, always challenged |
| Phone playing a video | 24 | **6** | 18: texture voter or challenge stopped them; the 6 accepted passed the resident passive gate |

**Replay against the challenge** (operators and supervisors always face the challenge): a real recording of the person, played from a random start without seeing the prompt, 80 sessions per setting with three challenges: 3, 4 and 2 accepted (3.8%, 5.0%, 2.5%; Wilson 95% upper bounds 10.5%, 12.2%, 8.7%).

**Determinism:** two runs of the evaluation on the same clips give a byte-identical `metrics.json`.

## 3. Red-team results

Two rounds (the second with seven reviewers: engine, fuzzing, desktop client, Android client, claims against evidence, repository security, evaluation statistics). Fixed and covered by the tests above, among others: a texture preprocessing step that rejected every real face; client configuration that never reached the engine; face login, operator onboarding and the exception photo stored without liveness; a repeated still frame accepted as live; images of 16000x16000 pixels hanging a session; a burst of 1000 sessions crashing the JVM; timestamps that defeated the minimum frame rate; non-repeatable evaluation runs. Every finding, fix and test is in `BUILD_LOG.md`.

## 4. Known failures and gaps

- A video of the person on a phone can pass the **resident** passive gate (6 of 24 above). Setting `mosip.registration.liveness.policy.mode.resident=ACTIVE_ALWAYS` closes it at the cost of a challenge in every registration.
- How often a real person passes the challenge has **not** been measured: it needs clips of people doing the prompted actions (`eval/tools/ACTION_RECALL.md`).
- The texture voter's answer changes with frame rate and compression on this camera; it is one voter of six and cannot pass anyone alone.
- No test ran on a real phone, on Windows, or against a MOSIP server.
