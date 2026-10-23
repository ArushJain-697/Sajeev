# ISO/IEC 30107 alignment

This is an alignment note, not a conformance claim. SAJEEV has not been tested by a lab and makes no certification claim.

## Terms (ISO/IEC 30107-1)

| Standard term | In SAJEEV |
|---|---|
| presentation attack (PA) | any attempt to show the camera something other than a live face of the person |
| presentation attack instrument (PAI) | the print, screen, mask or replay used |
| bona fide presentation | a person presenting their own live face |
| PAD subsystem | the five voters, the gate, challenges and re-evaluation |
| attack presentation classification error rate (APCER) | share of attacks accepted as live |
| bona fide presentation classification error rate (BPCER) | share of bona fide presentations rejected |

## Species (planned test set)

P1 printed photo, P2 screen photo, P3 video replay, P4 face-swap at capture (aimed at Binding), P5 bent print (aimed at V3), P6 print with eyes cut out (aimed at blink). Defined in `eval/RECORDING_PROTOCOL.md`. **No attack recording exists yet**; the only attack evidence is synthetic stand-ins.

## Metrics (ISO/IEC 30107-3 style) as implemented

`eval/run_eval.sh` reports APCER per species, max-APCER, BPCER, Wilson 95% intervals, breakdowns by subject and device, baselines (B0 no liveness, B1 texture CNN alone) and passive-stage ablations. The unit is one presentation (one clip); frames inside a clip are not counted separately. The plan is DEV for tuning and a sealed HELD-OUT set, differing in device, prints/screens, room and time, run once. With small n, intervals are wide: 0 events in 15 presentations still allows an upper bound near 20%. Results will be reported with that limit stated first. No result exists yet.

## ISO/IEC 19794-5 face image handling

The device returns an ISO 19794-5 face record with the image as JPEG 2000. The desktop bridge finds the JPEG 2000 data in the record, decodes it in memory (jai-imageio-jpeg2000, already in the client) and gives the engine a PNG for the Binding check. The header fields (pose, quality, expression) are not interpreted. The face stored for registration stays the device's original record.

## What we did not do

- No evaluation by an accredited lab; no 30107-3 test report.
- No level A/B/C PAI classification or attack potential rating.
- No interoperability test with a real L1 device; only the MOSIP mock devices (`docs/INTEROP_RESULTS.md`).
- No fairness or demographic evaluation beyond reporting per-subject rates once recordings exist.
