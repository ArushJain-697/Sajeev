// #48 (WS3) | The engine's own image type, so the engine runs on Android (which has no java.awt) and on the desktop. Java 11 only.
package io.sajeev.image;

/** An RGB image held as 0xAARRGGBB ints. Immutable in use: crop and resize return new images. */
public final class PixelImage {

    private final int width;
    private final int height;
    private final int[] argb;

    public PixelImage(int width, int height) {
        this(width, height, new int[Math.max(0, width) * Math.max(0, height)]);
    }

    public PixelImage(int width, int height, int[] argb) {
        if (width < 1 || height < 1 || argb.length != width * height) {
            throw new IllegalArgumentException("bad image size " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.argb = argb;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getRGB(int x, int y) {
        return argb[y * width + x];
    }

    public void setRGB(int x, int y, int rgb) {
        argb[y * width + x] = rgb;
    }

    /** @return grey level 0..255 (Rec. 601 luma) */
    public int grey(int x, int y) {
        int p = argb[y * width + x];
        return (int) Math.round(0.299 * ((p >> 16) & 0xFF) + 0.587 * ((p >> 8) & 0xFF) + 0.114 * (p & 0xFF));
    }

    /** @return a copy of the rectangle (must lie inside the image) */
    public PixelImage crop(int x, int y, int w, int h) {
        if (x < 0 || y < 0 || w < 1 || h < 1 || x + w > width || y + h > height) {
            throw new IllegalArgumentException("crop outside the image");
        }
        int[] out = new int[w * h];
        for (int r = 0; r < h; r++) {
            System.arraycopy(argb, (y + r) * width + x, out, r * w, w);
        }
        return new PixelImage(w, h, out);
    }

    /** @return a bilinear resize with pixel-centre alignment (the usual convention) */
    public PixelImage resize(int w, int h) {
        int[] out = new int[w * h];
        double fx = (double) width / w;
        double fy = (double) height / h;
        for (int y = 0; y < h; y++) {
            double sy = Math.max(0, Math.min(height - 1, (y + 0.5) * fy - 0.5));
            int y0 = (int) sy;
            int y1 = Math.min(height - 1, y0 + 1);
            double wy = sy - y0;
            for (int x = 0; x < w; x++) {
                double sx = Math.max(0, Math.min(width - 1, (x + 0.5) * fx - 0.5));
                int x0 = (int) sx;
                int x1 = Math.min(width - 1, x0 + 1);
                double wx = sx - x0;
                int shift = 16;
                int v = 0xFF000000;
                for (int c = 0; c < 3; c++, shift -= 8) {
                    double top = ((argb[y0 * width + x0] >> shift) & 0xFF) * (1 - wx) + ((argb[y0 * width + x1] >> shift) & 0xFF) * wx;
                    double bot = ((argb[y1 * width + x0] >> shift) & 0xFF) * (1 - wx) + ((argb[y1 * width + x1] >> shift) & 0xFF) * wx;
                    v |= ((int) Math.round(top * (1 - wy) + bot * wy) & 0xFF) << shift;
                }
                out[y * w + x] = v;
            }
        }
        return new PixelImage(w, h, out);
    }
}
