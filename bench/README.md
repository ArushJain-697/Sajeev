# Liveness Bench

Runs the real SAJEEV engine on a clip and prints the verdict, the five votes and the frame rate. Every output starts with
`BENCH · MOCK DEVICE · NO MOSIP SERVER`: it is a test harness, never a registration.

```
bench/run.sh --clip <folder of .jpg | file.jpg | file.mjpeg> [--fps 10] [--workflow RESIDENT] [--capture image.jpg]
```

- Needs the model directory: `SAJEEV_MODELS=<dir>` (or `--models`). When `../tools-local` exists, `run.sh` uses its Java, Maven and models.
- Frame arrival times are synthetic (frame index divided by `--fps`), so a run is repeatable.
- `--capture` runs the Binding check of a capture image against what was seen live.
- Exit code 0 = a verdict was printed; 2 = bad arguments; 3 = no decision before the clip ended.

Status: G1 (minimal command-line runner) is done. G3 (the JavaFX app with the real FXML screens for the resident, operator and supervisor flows) needs #42 to #45 and is not built yet.
