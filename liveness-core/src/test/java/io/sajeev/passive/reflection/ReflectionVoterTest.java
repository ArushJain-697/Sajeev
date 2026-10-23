// #22 (WS1) | Bible §6.4 V5 | Tests named as claims. SYNTHETIC glare on the public-domain face photo (not real prints).
package io.sajeev.passive.reflection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.sajeev.api.Vote;
import io.sajeev.face.Detection;
import java.awt.Color;
import java.awt.Graphics2D;
import io.sajeev.image.AwtDecoder;
import io.sajeev.image.PixelImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ReflectionVoterTest {

    /** TARGET limits; tuned on DEV only (#62). */
    private static final ReflectionVoter V = new ReflectionVoter(0.02, 4.0, 40, 215);
    /** Face box of one_face.jpg from the detector (22 Oct 2026). */
    private static final Detection FACE = new Detection(223, 59, 114, 104, 0.9);

    private static PixelImage photo() throws Exception {
        return AwtDecoder.from(ImageIO.read(new File("../testdata/fixtures-faces/one_face.jpg")));
    }

    @Test
    void test_glossy_print_glare_votes_attack() throws Exception {
        PixelImage img = photo();
        io.sajeev.testutil.TestImg.draw(img, g -> {
            g.setColor(Color.WHITE); // SYNTHETIC specular highlight across the cheek and nose
            g.fillOval((int) FACE.x() + 20, (int) FACE.y() + 25, 60, 40);
        });
        assertEquals(Vote.Label.ATTACK, V.vote(img, FACE).label());
    }

    @Test
    void test_matte_skin_does_not_vote_attack() throws Exception {
        assertEquals(Vote.Label.LIVE, V.vote(photo(), FACE).label());
    }

    @Test
    void test_extreme_lighting_abstains() throws Exception {
        PixelImage img = photo();
        PixelImage dark = new PixelImage(img.getWidth(), img.getHeight());
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                int r = (int) (((rgb >> 16) & 0xFF) * 0.06);
                int gg = (int) (((rgb >> 8) & 0xFF) * 0.06);
                int b = (int) ((rgb & 0xFF) * 0.06);
                dark.setRGB(x, y, (r << 16) | (gg << 8) | b);
            }
        }
        assertEquals(Vote.Label.ABSTAIN, V.vote(dark, FACE).label());
    }
}
