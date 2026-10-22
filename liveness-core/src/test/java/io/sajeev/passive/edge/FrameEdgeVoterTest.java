// #21 (WS1) | Bible §6.4 V4 | Tests named as claims. SYNTHETIC frames: a grey scene with a drawn bezel, and the same scene plain.
package io.sajeev.passive.edge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.sajeev.api.Vote;
import io.sajeev.face.Detection;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class FrameEdgeVoterTest {

    /** TARGET values; tune on DEV only (#62). */
    private static final FrameEdgeVoter V = new FrameEdgeVoter(0.5, 0.80, 12);

    /** A face-coloured box in a grey frame. SYNTHETIC: stands in for a face, not a real face. */
    private static BufferedImage scene(boolean bezel) {
        BufferedImage img = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(90, 90, 90));
        g.fillRect(0, 0, 640, 480);
        g.setColor(new Color(200, 160, 130));
        g.fillOval(260, 170, 120, 150); // the "face"
        if (bezel) {
            g.setColor(new Color(20, 20, 20));
            g.setStroke(new java.awt.BasicStroke(6));
            g.drawRect(200, 120, 240, 240); // a phone bezel framing the face
        }
        g.dispose();
        return img;
    }

    private static final Detection FACE = new Detection(260, 170, 120, 150, 0.9);

    @Test
    void test_held_phone_bezel_votes_attack() {
        assertEquals(Vote.Label.ATTACK, V.vote(scene(true), FACE).label());
    }

    @Test
    void test_plain_background_does_not_vote_attack() {
        assertEquals(Vote.Label.LIVE, V.vote(scene(false), FACE).label());
    }

    @Test
    void test_face_filling_the_frame_abstains() {
        Detection big = new Detection(0, 0, 640, 480, 0.9);
        assertEquals(Vote.Label.ABSTAIN, V.vote(scene(false), big).label());
    }
}
