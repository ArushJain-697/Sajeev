// #17 (WS1) | Bible §6.2, §6.6 | Head yaw, eye aspect ratio and mouth ratio from MediaPipe landmark indices. Java 11 only.
// Indices are MediaPipe's, not dlib's (BUILD_MAP #17). Yaw sign: positive = the person's own left (R1-11).
package io.sajeev.face;

/** Pure geometry on the landmark array. No model, no I/O. */
public final class FaceFeatures {

    /** Nose tip. */
    static final int NOSE = 1;
    /** Points at the face edges, the person's right and left (MediaPipe ids). */
    static final int EDGE_RIGHT = 234;
    static final int EDGE_LEFT = 454;
    /** Eye corners and lid points, MediaPipe ids. Left eye of the person (image right). */
    static final int[] EYE_LEFT = {362, 385, 387, 263, 373, 380};
    /** Right eye of the person (image left). */
    static final int[] EYE_RIGHT = {33, 160, 158, 133, 153, 144};
    /** Mouth corners and inner lips. */
    static final int MOUTH_LEFT = 291;
    static final int MOUTH_RIGHT = 61;
    static final int LIP_TOP = 13;
    static final int LIP_BOTTOM = 14;

    /** Iris landmark ids (MediaPipe): the person's right eye (image left) and left eye (image right), 5 points each. */
    static final int IRIS_RIGHT_FIRST = 468;
    static final int IRIS_LEFT_FIRST = 473;

    /** Gaze direction, as the person's own left and right. */
    public enum Gaze { CENTER, LEFT, RIGHT, UP, DOWN }

    private FaceFeatures() {
    }

    /**
     * Yaw proxy in [-1, 1]: how far the nose sits towards the person's left edge, relative to the face width.
     * 0 = facing the camera; sign is the person's left (positive). Not degrees; calibrated later.
     * @param p landmarks
     * @return the proxy
     */
    public static double yaw(float[][] p) {
        double mid = (p[EDGE_LEFT][0] + p[EDGE_RIGHT][0]) / 2.0;
        double width = Math.abs(p[EDGE_LEFT][0] - p[EDGE_RIGHT][0]);
        if (width <= 0) {
            return 0.0;
        }
        return 2.0 * (p[NOSE][0] - mid) / width;
    }

    /**
     * Eye aspect ratio: vertical lid opening over horizontal eye width. About 0.3 open, below about 0.2 closed.
     * @param p landmarks
     * @param eye EYE_LEFT or EYE_RIGHT
     * @return the ratio
     */
    public static double eyeAspectRatio(float[][] p, int[] eye) {
        double v1 = dist(p[eye[1]], p[eye[5]]);
        double v2 = dist(p[eye[2]], p[eye[4]]);
        double h = dist(p[eye[0]], p[eye[3]]);
        return h <= 0 ? 0.0 : (v1 + v2) / (2.0 * h);
    }

    /**
     * Mouth aperture over mouth width (a smile widens, an open mouth raises the aperture).
     * @param p landmarks
     * @return aperture ratio
     */
    public static double mouthAspectRatio(float[][] p) {
        double w = dist(p[MOUTH_LEFT], p[MOUTH_RIGHT]);
        return w <= 0 ? 0.0 : dist(p[LIP_TOP], p[LIP_BOTTOM]) / w;
    }

    /** Mouth width over the face width. A smile raises it. */
    public static double mouthWidthRatio(float[][] p) {
        double faceW = Math.abs(p[EDGE_LEFT][0] - p[EDGE_RIGHT][0]);
        return faceW <= 0 ? 0.0 : dist(p[MOUTH_LEFT], p[MOUTH_RIGHT]) / faceW;
    }

    private static double dist(float[] a, float[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Iris offset inside the eye, averaged over both eyes: (iris centre minus eye midpoint) divided by eye width.
     * x is positive toward the person's own left (the right of the camera image), y is positive downward.
     * @param p landmarks (478 points)
     * @return {x, y}
     */
    public static double[] gaze(float[][] p) {
        double[] a = irisOffset(p, IRIS_RIGHT_FIRST, EYE_RIGHT);
        double[] b = irisOffset(p, IRIS_LEFT_FIRST, EYE_LEFT);
        return new double[] {(a[0] + b[0]) / 2.0, (a[1] + b[1]) / 2.0};
    }

    private static double[] irisOffset(float[][] p, int first, int[] eye) {
        double cx = 0;
        double cy = 0;
        for (int i = 0; i < 5; i++) {
            cx += p[first + i][0];
            cy += p[first + i][1];
        }
        cx /= 5;
        cy /= 5;
        double mx = (p[eye[0]][0] + p[eye[3]][0]) / 2.0;
        double my = (p[eye[0]][1] + p[eye[3]][1]) / 2.0;
        double w = Math.abs(p[eye[3]][0] - p[eye[0]][0]);
        return w <= 0 ? new double[] {0, 0} : new double[] {(cx - mx) / w, (cy - my) / w};
    }

    /**
     * Classify the gaze relative to the person's neutral gaze (measured while they look at the camera).
     * Limits are constructor-style parameters (TARGET, tune on DEV, #62); they are not literals in the logic.
     * @param g the current gaze from {@link #gaze}
     * @param neutral the gaze at rest
     * @param horizontal least horizontal change that counts as LEFT or RIGHT
     * @param up least upward change that counts as UP (a positive number)
     * @param down least downward change that counts as DOWN (a positive number)
     * @return the direction, or CENTER
     */
    public static Gaze classifyGaze(double[] g, double[] neutral, double horizontal, double up, double down) {
        double dx = g[0] - neutral[0];
        double dy = g[1] - neutral[1];
        if (Math.abs(dx) >= horizontal && Math.abs(dx) >= Math.abs(dy)) {
            return dx > 0 ? Gaze.LEFT : Gaze.RIGHT;
        }
        if (-dy >= up) {
            return Gaze.UP;
        }
        if (dy >= down) {
            return Gaze.DOWN;
        }
        return Gaze.CENTER;
    }

    /** @param p landmarks @param threshold EAR below this counts as closed (TARGET 0.12) @return true when both eyes are closed */
    public static boolean eyesClosed(float[][] p, double threshold) {
        return eyeAspectRatio(p, EYE_LEFT) < threshold && eyeAspectRatio(p, EYE_RIGHT) < threshold;
    }

    /**
     * Pitch proxy: where the nose tip sits between the eye line and the mouth, 0 at the eyes and 1 at the mouth.
     * Tilting the head up moves the nose toward the eye line (smaller value); down moves it toward the mouth.
     * Use it relative to the person's neutral value. Not degrees; calibrated later.
     * @param p landmarks
     * @return the proxy
     */
    public static double pitch(float[][] p) {
        double eyeY = (p[EYE_LEFT[0]][1] + p[EYE_LEFT[3]][1] + p[EYE_RIGHT[0]][1] + p[EYE_RIGHT[3]][1]) / 4.0;
        double mouthY = (p[LIP_TOP][1] + p[LIP_BOTTOM][1]) / 2.0;
        double span = mouthY - eyeY;
        return span <= 0 ? 0.0 : (p[NOSE][1] - eyeY) / span;
    }
}
