# Face fixtures (#16)

Synthetic fixtures for the detector and tracker tests. They are NOT a dataset and NOT evaluation data.

| File | What it is | Source |
|---|---|---|
| one_face.jpg | One face, 640x480 | scikit-image `data.astronaut()` (NASA photo, public domain), resized |
| zero_faces.jpg | Plain grey, no face | generated |
| two_faces_composite.jpg | Two faces side by side | the same photo twice, one mirrored. **Synthetic composite** |
| dark_one_face.jpg | One face at 6% brightness | generated from one_face.jpg |
| blurry_one_face.jpg | One face, Gaussian blur radius 12 | generated from one_face.jpg |

Limitation (stated plainly): one real person only. Real diverse fixtures (ages, skin tones, glasses, lighting) come with #59 and the recording protocol.
