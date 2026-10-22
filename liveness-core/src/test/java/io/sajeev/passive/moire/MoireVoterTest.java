// #19 (WS1) | Bible §6.4 V2 | Tests named as claims. SYNTHETIC moiré (an added grating) on the public-domain face photo.
package io.sajeev.passive.moire;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class MoireVoterTest {

    private static final double LIMIT = 0.20;

    private static BufferedImage clean() throws Exception {
        BufferedImage src = ImageIO.read(new File("../testdata/fixtures-faces/one_face.jpg"));
        BufferedImage g = new BufferedImage(200, 200, BufferedImage.TYPE_BYTE_GRAY);
        g.createGraphics().drawImage(src.getSubimage(100, 60, 200, 200), 0, 0, null);
        return g;
    }

    private static BufferedImage moire(BufferedImage g) {
        BufferedImage m = new BufferedImage(200, 200, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 200; x++) {
                int v = g.getRaster().getSample(x, y, 0);
                double add = 40 * Math.cos(2 * Math.PI * x / 4) + 40 * Math.cos(2 * Math.PI * y / 4);
                m.getRaster().setSample(x, y, 0, (int) Math.max(0, Math.min(255, v + add)));
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
