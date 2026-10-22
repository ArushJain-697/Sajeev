// #41 (WS2) | Renders UX1-UX14 to PNG files with JavaFX snapshots. Usage: ScreenshotMain <outDir> [lang]
package io.sajeev.ui;

import io.sajeev.guidance.GuidanceKeys;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;

/** Not a test: a small tool that needs a display (it runs on the Mac session). */
public final class ScreenshotMain {

    private ScreenshotMain() {
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        String lang = args.length > 1 ? args[1] : "en";
        out.mkdirs();
        GuidanceKeys g = new GuidanceKeys();
        List<UiState> states = UiStates.allUx();
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await();
        for (int i = 0; i < states.size(); i++) {
            final int n = i + 1;
            final UiState st = states.get(i);
            CountDownLatch done = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(ScreenshotMain.class.getResource("liveness-pane.fxml"));
                    Parent root = loader.load();
                    LivenessPaneController c = loader.getController();
                    c.init(g, lang);
                    c.show(st);
                    Scene scene = new Scene(root, 420, 560);
                    WritableImage img = scene.snapshot(null);
                    BufferedImage bi = SwingFXUtils.fromFXImage(img, null);
                    javax.imageio.ImageIO.write(bi, "png", new File(out, String.format("ux%02d-%s.png", n, lang)));
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    done.countDown();
                }
            });
            done.await();
        }
        Platform.exit();
        System.out.println("wrote " + states.size() + " screenshots to " + out);
    }
}
