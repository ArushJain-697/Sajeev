# Recording protocol (#59)

Purpose: record the clips that the DEV tuning set and the sealed HELD-OUT set are made of. Written so that Arush can run the DEV session from this page alone.

## Unit
One **presentation** = one clip of about 10 seconds, one person (or one attack object), one device, one distance. Results are counted per presentation, never per frame.

## Species labels (the only valid first word of a file name)
- `bona` = real live person.
- `P1` printed photo (glossy and matte).
- `P2` photo on a phone screen.
- `P3` video replay on a screen.
- `P4` live-then-swap (live face, then a printed photo held in front of the camera).
- `P5` bent print (a curved photo).
- `P6` cut-out print mask (eyes cut out).

A file without one of these labels is rejected by `ingest.sh`.

## Sizes (D31, TARGET)
- DEV: at least 40 bona fide presentations, and at least 30 per attack species.
- HELD-OUT: at least 60 bona fide, and at least 40 per attack species.

## Bona fide shot list (per subject, DEV session)
- Three lighting conditions: window light, room light, dim light.
- Glasses on and off, if the subject wears them.
- Two kinds of clip: still for 10 s, and following the on-screen prompts (blink, smile, turn left/right).
- Distance: about 50 cm and about 80 cm from the camera.
- Camera: the DEV device only (see D32 for what HELD-OUT changes).

## Attack shot list (per attack species, DEV session)
- Use the same room and camera as the bona fide clips.
- P1: glossy print and matte print, each held flat, then slightly tilted.
- P2: phone screen showing the photo, brightness at 50% and 100%.
- P3: video of the same subject playing on a laptop screen.
- P4: live face for 3 s, then the printed photo replaces it in front of the camera.
- P5: print bent around a cup, held at the same distance.
- P6: print with the eyes cut out, eyes shown by the live person behind it.
- Record the prints or screens used (kind, size, brand) in `eval/clips/<session>/notes.md`.

## Devices and rooms
- DEV: the laptop webcam and one phone camera, in room A.
- HELD-OUT: a different camera, different prints and screens, a different room, and a different day (D32). The HELD-OUT clips are sealed under HELDOUT_PROTOCOL.md and not opened before the seal.

## Consent
- Every subject signs `eval/consent/CONSENT_FORM.md` before the first clip.
- Clips are used only for this hackathon and are deleted at the end (CLEANUP.md).
- Subjects may withdraw; their clips are removed from the sets and the removal is logged in `eval/CHANGELOG.md`.

## Steps for a DEV session (Arush)
1. Fill in the consent form for each subject. Keep the signed copy outside the repo.
2. Name each raw file by species, for example `bona_glasses_off_01.mov` or `P1_glossy_01.mov`.
3. Put all raw files for one session in one folder.
4. Run: `eval/ingest.sh <session-id> <subject-id> <device-id> <raw-folder>`
5. Check the printed summary: count per species against the sizes above.
6. Commit the manifest only (`eval/clips/<session>/manifest.csv`). Never commit the clips.

## What is NOT in this protocol
- HELD-OUT recording dates and rooms (see HELDOUT_PROTOCOL.md).
- Any real-person data for the Android demo (the demo uses the mock devices and consented DEV clips).
