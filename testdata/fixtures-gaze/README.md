# Synthetic gaze and closed-eye fixtures (#17)

**These are SYNTHETIC. They are not real people looking in different directions.** Each file is the public-domain face photo (`../fixtures-faces/one_face.jpg`) with the eyes edited by `eval/rig/make_synthetic_gaze.py`: the iris is moved inside the eye, or the eyes are painted over with skin colour and a lid line. They exist so the landmark features and the gaze classifier can be tested before real clips exist. They do not prove the detector works on real faces.

Convention: `look_left` is the person's own left, which is the right side of the camera image (+x).
Files: neutral, look_left, look_right, look_up, look_down, eyes_closed.
Real clips (consented, #59) replace these before any claim about real performance.
