// #diag (WS2) | The diagnostic panel (PDF good-to-have: "diagnostic mode for device and liveness troubleshooting"). Java 17 here.
// It only draws what EngineSession.diagnostics() returns, which is empty unless an administrator enabled diagnostic mode and the
// viewer is the operator. A persistent banner says so. Nothing here ever reads an image or a template.
package io.sajeev.ui;

import io.sajeev.audit.DiagnosticMode;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Builds the panel as a node. Each row is a label and a value, so a screen reader reads them as pairs. */
public final class DiagnosticPane {

    private DiagnosticPane() {
    }

    /**
     * @param data the map from EngineSession.diagnostics (empty when not allowed)
     * @return the panel; when {@code data} is empty it shows nothing at all (no banner, no rows)
     */
    public static VBox build(Map<String, String> data) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(12));
        if (data.isEmpty()) {
            box.setVisible(false);
            box.setManaged(false);
            return box;
        }
        Label banner = new Label(DiagnosticMode.BANNER);
        banner.setStyle("-fx-background-color: #b3261e; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 4 10 4 10;");
        banner.setAccessibleText(DiagnosticMode.BANNER + ": technical information for troubleshooting");
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(4);
        ColumnConstraints key = new ColumnConstraints();
        key.setMinWidth(170);
        ColumnConstraints val = new ColumnConstraints();
        val.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(key, val);
        int r = 0;
        for (Map.Entry<String, String> e : data.entrySet()) {
            Label k = new Label(e.getKey());
            k.setStyle("-fx-text-fill: #4a5560;");
            Label v = new Label(e.getValue());
            v.setWrapText(true);
            v.setMaxWidth(260);
            k.setAccessibleText(e.getKey() + ": " + e.getValue());
            grid.add(k, 0, r);
            grid.add(v, 1, r);
            r++;
        }
        box.getChildren().addAll(banner, grid);
        return box;
    }
}
