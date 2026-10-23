// #48 (WS3) | Decoding of JPEG/PNG bytes into a PixelImage. The decoder is pluggable: the desktop uses ImageIO
// (loaded by name, so nothing on Android refers to java.awt), Android installs a BitmapFactory decoder. Java 11 only.
package io.sajeev.image;

/** Entry point for decoding. */
public final class ImageCodec {

    /** A decoder for encoded image bytes. */
    public interface Decoder {
        /** @return the image, or null when the bytes are not a decodable image */
        PixelImage decode(byte[] encoded);
    }

    private static volatile Decoder decoder;

    private ImageCodec() {
    }

    /** @param d the decoder to use (Android calls this once at start-up) */
    public static void install(Decoder d) {
        decoder = d;
    }

    /** @return the image, or null when undecodable (never throws) */
    public static PixelImage decode(byte[] encoded) {
        try {
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
