// #25 (WS1) | Bible §6.6 | The per-frame numbers the action detectors read. Java 11 only. Immutable.
package io.sajeev.active;

/**
 * Features of one frame. Left and right are the person's OWN left and right (D28): positive yaw and positive gazeX
 * mean the person's left, which is the right side of the camera image. All values are ratios, never pixels.
 */
public final class FrameFeatures {
    private final long sequence;
    private final long arrivalNanos;
    private final double yaw;
    private final double pitch;
    private final double ear;
    private final double mouthAperture;
    private final double mouthWidth;
    private final double gazeX;
    private final double gazeY;

    public FrameFeatures(long sequence, long arrivalNanos, double yaw, double pitch, double ear,
                         double mouthAperture, double mouthWidth, double gazeX, double gazeY) {
        this.sequence = sequence;
        this.arrivalNanos = arrivalNanos;
        this.yaw = yaw;
        this.pitch = pitch;
        this.ear = ear;
        this.mouthAperture = mouthAperture;
        this.mouthWidth = mouthWidth;
        this.gazeX = gazeX;
        this.gazeY = gazeY;
    }

    public long sequence() { return sequence; }
    public long arrivalNanos() { return arrivalNanos; }
    public double yaw() { return yaw; }
    public double pitch() { return pitch; }
    public double ear() { return ear; }
    public double mouthAperture() { return mouthAperture; }
    public double mouthWidth() { return mouthWidth; }
    public double gazeX() { return gazeX; }
    public double gazeY() { return gazeY; }
}
