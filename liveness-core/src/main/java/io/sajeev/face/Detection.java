// #16 (WS1) | Bible §6.2 | One detected face box in original frame pixels. Immutable. Java 11 only.
package io.sajeev.face;

/** A face box in the coordinates of the original image, with its detector score. */
public final class Detection {
    private final double x;
    private final double y;
    private final double w;
    private final double h;
    private final double score;

    /**
     * @param x left edge in pixels
     * @param y top edge in pixels
     * @param w width in pixels
     * @param h height in pixels
     * @param score detector confidence in [0, 1]
     */
    public Detection(double x, double y, double w, double h, double score) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.score = score;
    }

    /** @return left edge */
    public double x() {
        return x;
    }

    /** @return top edge */
    public double y() {
        return y;
    }

    /** @return width */
    public double w() {
        return w;
    }

    /** @return height */
    public double h() {
        return h;
    }

    /** @return detector score */
    public double score() {
        return score;
    }
}
