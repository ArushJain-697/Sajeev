// #48 (WS3) | Desktop decoder using ImageIO. Only loaded by name from ImageCodec; never on Android. Java 11 only.
package io.sajeev.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** ImageIO-backed decoder (also used by tests to read files). */
public final class AwtDecoder implements ImageCodec.Decoder {

    @Override
    public PixelImage decode(byte[] encoded) {
        try {
            return from(ImageIO.read(new ByteArrayInputStream(encoded)));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** @return the same pixels as a PixelImage, or null for null */
    public static PixelImage from(BufferedImage b) {
        if (b == null) {
            return null;
        }
        int w = b.getWidth();
        int h = b.getHeight();
        int[] px = b.getRGB(0, 0, w, h, null, 0, w);
        return new PixelImage(w, h, px);
    }
}
