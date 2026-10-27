// #48 (WS3) | Decoding of JPEG/PNG bytes into a PixelImage. The decoder is pluggable: the desktop uses ImageIO
// (loaded by name, so nothing on Android refers to java.awt), Android installs a BitmapFactory decoder. Java 11 only.
// Red-team 2 fix: the declared size of a JPEG/PNG is read from its header and refused BEFORE any pixel memory is allocated.
package io.sajeev.image;

/** Entry point for decoding. */
public final class ImageCodec {

    /** A decoder for encoded image bytes. */
    public interface Decoder {
        /** @return the image, or null when the bytes are not a decodable image */
        PixelImage decode(byte[] encoded);
    }

    /** Default cap on the width or height of an image we agree to decode. */
    public static final int DEFAULT_MAX_SIDE = 8192;
    /** Default cap on width x height (40 megapixels is about 160 MB of ARGB ints). */
    public static final long DEFAULT_MAX_PIXELS = 40_000_000L;

    private static volatile Decoder decoder;
    private static volatile int maxSide = DEFAULT_MAX_SIDE;
    private static volatile long maxPixels = DEFAULT_MAX_PIXELS;

    private ImageCodec() {
    }

    /** @param d the decoder to use (Android calls this once at start-up) */
    public static void install(Decoder d) {
        decoder = d;
    }

    /**
     * Set the size limits (the engine passes its EngineSettings values).
     * @param side largest allowed width or height
     * @param pixels largest allowed width x height
     */
    public static void setLimits(int side, long pixels) {
        maxSide = Math.max(1, side);
        maxPixels = Math.max(1L, pixels);
    }

    /**
     * Read the size an image DECLARES in its header, without decoding anything.
     * @param b the encoded bytes
     * @return {width, height} for a JPEG or PNG with a readable header; null when the format is unknown or the header is damaged
     */
    public static int[] declaredSize(byte[] b) {
        if (b == null || b.length < 10) {
            return null;
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) {
            return jpegSize(b);
        }
        if (b.length >= 24 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            long w = u32(b, 16);
            long h = u32(b, 20);
            return w > Integer.MAX_VALUE || h > Integer.MAX_VALUE ? new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}
                    : new int[] {(int) w, (int) h};
        }
        return null;
    }

    private static long u32(byte[] b, int o) {
        return ((long) (b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }

    private static int[] jpegSize(byte[] b) {
        int i = 2;
        while (i + 3 < b.length) {
            if ((b[i] & 0xFF) != 0xFF) {
                i++;
                continue;
            }
            int m = b[i + 1] & 0xFF;
            if (m == 0xFF) { // fill byte
                i++;
                continue;
            }
            if (m == 0x00 || m == 0x01 || (m >= 0xD0 && m <= 0xD8)) { // stuffed byte, TEM, RSTn, SOI: no length
                i += 2;
                continue;
            }
            if (m == 0xD9 || m == 0xDA) { // EOI or SOS before any SOF: damaged
                return null;
            }
            int len = ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
            boolean sof = m >= 0xC0 && m <= 0xCF && m != 0xC4 && m != 0xC8 && m != 0xCC;
            if (sof) {
                if (i + 8 >= b.length) {
                    return null;
                }
                int h = ((b[i + 5] & 0xFF) << 8) | (b[i + 6] & 0xFF);
                int w = ((b[i + 7] & 0xFF) << 8) | (b[i + 8] & 0xFF);
                return new int[] {w, h};
            }
            if (len < 2) {
                return null;
            }
            i += 2 + len;
        }
        return null;
    }

    /** @return true when the bytes declare an image larger than the limits (such bytes are never given to a decoder) */
    public static boolean tooLarge(byte[] encoded) {
        int[] s = declaredSize(encoded);
        if (s == null) {
            return false;
        }
        return s[0] > maxSide || s[1] > maxSide || (long) s[0] * s[1] > maxPixels;
    }

    /** @return the image, or null when undecodable or larger than the limits (an OutOfMemoryError is not swallowed here: the session worker and checkCapture catch it and fail closed) */
    public static PixelImage decode(byte[] encoded) {
        try {
            if (encoded == null || tooLarge(encoded)) {
                return null;
            }
            Decoder d = decoder;
            if (d == null) {
                synchronized (ImageCodec.class) {
                    if (decoder == null) {
                        decoder = (Decoder) Class.forName("io.sajeev.image.AwtDecoder").getDeclaredConstructor().newInstance();
                    }
                    d = decoder;
                }
            }
            return d.decode(encoded);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }
}
