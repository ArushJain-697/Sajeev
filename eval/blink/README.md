# Blink recall experiment (#61)

1. Record about 10 short clips of one deliberate blink each (30 fps or more, face centred), with consent.
2. `java -cp "eval/target/classes:$(cat eval/target/cp.txt)" io.sajeev.eval.ExtractEarTrace <models> clip.mp4 eval/blink/real/blink01.csv 30` for each clip.
3. `eval/blink/run_blink.sh eval/blink/real` prints the recall table for the real blinks and writes the chosen minimum fps.
4. Put that value into `EngineSession.BLINK_MIN_FPS` and the DECISION_MAP (D34); the test `test_blink_minimum_in_config_equals_value_from_experiment` then needs the committed result file to match the real-trace run, so re-run the test with the same traces (or update the test data path).
