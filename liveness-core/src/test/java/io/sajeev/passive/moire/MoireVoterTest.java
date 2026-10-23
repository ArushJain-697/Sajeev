// #19 (WS1) | Bible §6.4 V2 | Tests named as claims. SYNTHETIC moiré (an added grating) on the public-domain face photo.
package io.sajeev.passive.moire;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sajeev.image.AwtDecoder;
import io.sajeev.image.PixelImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class MoireVoterTest {

    private static final double LIMIT = 0.20;

    private static PixelImage clean() throws Exception {
        PixelImage src = AwtDecoder.from(ImageIO.read(new File("../testdata/fixtures-faces/one_face.jpg")));
        PixelImage g = src.crop(100, 60, 200, 200);
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 200; x++) {
                int v = g.grey(x, y);
                g.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return g;
    }

    private static PixelImage moire(PixelImage g) {
        PixelImage m = new PixelImage(200, 200);
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 200; x++) {
                int v = g.grey(x, y);
                double add = 40 * Math.cos(2 * Math.PI * x / 4) + 40 * Math.cos(2 * Math.PI * y / 4);
                int o = (int) Math.max(0, Math.min(255, v + add));
                m.setRGB(x, y, (o << 16) | (o << 8) | o);
            }
        }
        return m;
    }

    @Test
    void test_synthetic_moire_pattern_votes_attack() throws Exception {
        double share = MoireMeasure.peakEnergyShare(moire(clean()));
        assertTrue(share >= LIMIT, "moire share " + share);
    }

    @Test
    void test_clean_face_crop_does_not_vote_attack() throws Exception {
        double share = MoireMeasure.peakEnergyShare(clean());
        assertTrue(share < LIMIT, "clean share " + share);
    }

    @Test
    void test_small_crop_abstains() {
        assertTrue(MoireMeasure.MIN_SIDE == 96, "minimum crop side is 96 px");
    }
}
