# #17: SYNTHETIC gaze and closed-eye fixtures. They are the public-domain face photo with the eyes edited. NOT real people.
# Run with the tools-local venv (needs the landmark ONNX): python eval/rig/make_synthetic_gaze.py <models-dir> <out-dir>
# Convention: look_left = the person's own left = the iris moves toward the RIGHT of the camera image (+x).
import sys, numpy as np, onnxruntime as ort
from PIL import Image, ImageDraw, ImageFilter
models, out = sys.argv[1], sys.argv[2]
S = ort.InferenceSession(models + "/landmark/face_landmarks_detector.onnx", providers=["CPUExecutionProvider"])
FACE = (223, 59, 114, 104)  # face box of one_face.jpg from the YuNet detector
EYE_L = [362, 385, 387, 263, 373, 380]; EYE_R = [33, 160, 158, 133, 153, 144]
def landmarks(img):
    x, y, w, h = FACE; side = max(w, h) * 1.5; cx, cy = x + w / 2, y + h / 2; x0, y0 = cx - side / 2, cy - side / 2
    crop = img.crop((int(x0), int(y0), int(x0 + side), int(y0 + side))).resize((256, 256), Image.BILINEAR)
    a = np.asarray(crop.convert("RGB")).astype(np.float32) / 255
    o = S.run(None, {S.get_inputs()[0].name: a[None]})[0].reshape(-1, 3)
    return np.stack([x0 + o[:, 0] * side / 256, y0 + o[:, 1] * side / 256], 1)
base = Image.open("testdata/fixtures-faces/one_face.jpg").convert("RGB")
p = landmarks(base); arr = np.asarray(base)
skin = tuple(int(v) for v in arr[110:118, 270:280].reshape(-1, 3).mean(0))
def closed():
    im = base.copy(); d = ImageDraw.Draw(im)
    for e in (EYE_L, EYE_R):
        xs = [p[i][0] for i in e]; ys = [p[i][1] for i in e]
        cx, cy = np.mean(xs), np.mean(ys); w = (max(xs) - min(xs)) / 2 + 2; h = (max(ys) - min(ys)) / 2 + 2
        d.ellipse((cx - w, cy - h, cx + w, cy + h), fill=skin); d.line((cx - w, cy, cx + w, cy), fill=(60, 40, 35), width=2)
    return im.filter(ImageFilter.GaussianBlur(0.6))
def gaze(dx, dy):
    im = base.copy(); d = ImageDraw.Draw(im)
    for rng, e in ((range(468, 473), EYE_R), (range(473, 478), EYE_L)):
        c = p[list(rng)].mean(0); r = max(np.linalg.norm(p[list(rng)[1]] - c), 3.5); ew = abs(p[e[3]][0] - p[e[0]][0])
        d.ellipse((c[0] - r * 1.5, c[1] - r * 1.5, c[0] + r * 1.5, c[1] + r * 1.5), fill=(225, 215, 210))
        nx, ny = c[0] + dx * ew * 0.28, c[1] + dy * ew * 0.28
        d.ellipse((nx - r, ny - r, nx + r, ny + r), fill=(45, 30, 25))
    return im.filter(ImageFilter.GaussianBlur(0.5))
base.save(out + "/neutral.jpg", quality=95)
closed().save(out + "/eyes_closed.jpg", quality=95)
for name, dx, dy in (("look_left", 1, 0), ("look_right", -1, 0), ("look_up", 0, -1), ("look_down", 0, 1)):
    gaze(dx, dy).save(out + "/%s.jpg" % name, quality=95)
print("wrote 6 synthetic fixtures to", out)
