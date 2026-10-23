// Test helper: drawing with java.awt on a PixelImage (the engine itself no longer uses java.awt). Desktop tests only.
package io.sajeev.testutil;

import io.sajeev.image.AwtDecoder;
import io.sajeev.image.PixelImage;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

public final class TestImg {
    private TestImg() {
    }

    public static BufferedImage toBuf(PixelImage p) {
        BufferedImage b = new BufferedImage(p.getWidth(), p.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < p.getHeight(); y++) {
            for (int x = 0; x < p.getWidth(); x++) {
                b.setRGB(x, y, p.getRGB(x, y));
            }
        }
        return b;
    }

    /** Runs the drawing code on a copy and writes the pixels back into the same PixelImage. */
    public static void draw(PixelImage p, Consumer<Graphics2D> painter) {
        BufferedImage b = toBuf(p);
        Graphics2D g = b.createGraphics();
        painter.accept(g);
        g.dispose();
        for (int y = 0; y < p.getHeight(); y++) {
            for (int x = 0; x < p.getWidth(); x++) {
                p.setRGB(x, y, b.getRGB(x, y));
            }
        }
    }

    public static PixelImage from(BufferedImage b) {
        return AwtDecoder.from(b);
    }
}
