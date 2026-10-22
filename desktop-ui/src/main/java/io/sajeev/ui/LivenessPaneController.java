// #41 (WS2) | docs/UIUX | Applies a UiState to the FXML pane. Call show() on the JavaFX thread (or use showLater).
package io.sajeev.ui;

import io.sajeev.guidance.GuidanceKeys;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.NodeOrientation;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;

/** Controller for liveness-pane.fxml. Does no thinking: it shows what the state says. */
public class LivenessPaneController {

    @FXML private VBox root;
    @FXML private Label title;
    @FXML private Label badge;
    @FXML private StackPane guide;
    @FXML private Label icon;
    @FXML private HBox dots;
    @FXML private Label message;
    @FXML private VBox hints;
    @FXML private HBox buttons;
    @FXML private Label footer;

    private GuidanceKeys guidance;
    private String lang = "en";

    /**
     * @param guidance the message bundles
     * @param lang "en", "fr" or "ar"; Arabic lays the pane out right to left
     */
    public void init(GuidanceKeys guidance, String lang) {
        this.guidance = guidance;
        this.lang = lang;
        root.setNodeOrientation(GuidanceKeys.isRtl(lang) ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
    }

    /** Safe from any thread: runs show() on the JavaFX thread. */
    public void showLater(UiState s) {
        Platform.runLater(() -> show(s));
    }

    /** Must run on the JavaFX thread. */
    public void show(UiState s) {
        title.setText(guidance.text(s.titleKey(), lang));
        badge.setText(guidance.text(s.badgeKey(), lang));
        badge.setStyle("-fx-padding: 4 10 4 10; -fx-background-radius: 12; -fx-font-weight: bold; " + badgeColours(s.badgeStyle()));
        guide.setVisible(s.showGuide());
        guide.setManaged(s.showGuide());
        guide.setAccessibleText(guidance.text("hint.capture_area", lang));
        icon.setText(s.icon());
        dots.getChildren().clear();
        for (int i = 0; i < s.progressTotal(); i++) {
            Circle c = new Circle(8);
            c.setStroke(Color.web("#0b5cab"));
            c.setStrokeWidth(2);
            c.setFill(i < s.progressDone() ? Color.web("#0b5cab") : Color.WHITE);
            dots.getChildren().add(c);
        }
        dots.setAccessibleText(s.progressDone() + " / " + s.progressTotal());
        dots.setVisible(s.progressTotal() > 0);
        dots.setManaged(s.progressTotal() > 0);
        message.setText(guidance.text(s.messageKey(), lang));
        hints.getChildren().clear();
        for (String k : s.hintKeys()) {
            Label h = new Label("• " + guidance.text(k, lang));
            h.setStyle("-fx-text-fill: #4a5560; -fx-font-size: 0.9em;");
            hints.getChildren().add(h);
        }
        buttons.getChildren().clear();
        for (String k : s.buttonKeys()) {
            Button b = new Button(guidance.text(k, lang));
            b.setMinHeight(44);
            b.setAccessibleText(b.getText());
            b.setStyle("-fx-background-color: #ffffff; -fx-border-color: #0b5cab; -fx-border-width: 2; -fx-border-radius: 8; -fx-text-fill: #0b5cab;");
            buttons.getChildren().add(b);
        }
        String f = s.footerKey().isEmpty() ? "" : guidance.text(s.footerKey(), lang).replace("{0}", "2").replace("{1}", "3");
        footer.setText(f);
        footer.setVisible(!f.isEmpty());
        footer.setManaged(!f.isEmpty());
    }

    private static String badgeColours(UiState.Style st) {
        switch (st) {
            case BAD: return "-fx-background-color: #b3261e; -fx-text-fill: #ffffff;";
            case OK: return "-fx-background-color: #1b7a3a; -fx-text-fill: #ffffff;";
            case WARN: return "-fx-background-color: #f2b705; -fx-text-fill: #1b1f24;";
            default: return "-fx-background-color: #0b5cab; -fx-text-fill: #ffffff;";
        }
    }
}
