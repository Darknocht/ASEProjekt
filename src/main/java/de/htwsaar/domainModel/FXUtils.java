package de.htwsaar.domainModel;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ProgressIndicator;

public class FXUtils {

    private FXUtils() {
        // Prevent instantiation
    }

    public static void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    public static void showProgress(ProgressIndicator indicator, boolean show) {
        Platform.runLater(() -> indicator.setVisible(show));
    }
}
