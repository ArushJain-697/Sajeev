// (WS2) | Renders the diagnostic panel and the pane with a camera picture. Needs a display. Usage: ExtraScreenshotsMain <outDir> <face.jpg>
package io.sajeev.ui;

import io.sajeev.guidance.GuidanceKeys;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

public final class ExtraScreenshotsMain {
    private ExtraScreenshotsMain() {
    }

    public static void main(String[] a) throws Exception {
        File out = new File(a[0]);
        out.mkdirs();
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                GuidanceKeys g = new GuidanceKeys();
                FXMLLoader loader = new FXMLLoader(ExtraScreenshotsMain.class.getResource("liveness-pane.fxml"));
                Parent root = loader.load();
                LivenessPaneController c = loader.getController();
                c.init(g, "en");
                c.show(UiStates.ux1());
                c.setPreview(new Image(new File(a[1]).toURI().toString()));
                save(new Scene(root, 420, 640), new File(out, "ux1-with-camera-picture-en.png"));
                Map<String, String> d = new LinkedHashMap<>();
                d.put("state", "DONE");
                d.put("verdict", "ATTACK_SUSPECTED / PAD_FAIL / attack_during_challenge");
                d.put("delivered fps", "15.2");
                d.put("minimum fps (config)", "3.0");
                d.put("queue depth", "0");
                d.put("avg processing ms per frame", "96");
                d.put("last guidance", "none");
                d.put("voter V1_texture_minifasnet", "ATTACK (LOW) texture_prob=0.016");
                d.put("voter V2_moire", "LIVE (HIGH) moire_flagged=0 of 3");
                d.put("voter V3_flatness", "ABSTAIN (no score) no_motion");
                save(new Scene(DiagnosticPane.build(d), 520, 420), new File(out, "diagnostic-panel-en.png"));
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
    }

    private static void save(Scene scene, File f) throws Exception {
        WritableImage img = scene.snapshot(null);
        BufferedImage bi = SwingFXUtils.fromFXImage(img, null);
        javax.imageio.ImageIO.write(bi, "png", f);
        System.out.println("wrote " + f);
    }
}
