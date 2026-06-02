package org.rac.gui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

public class MessageDialogViewController {

    @FXML private HBox  headerBar;
    @FXML private Label iconLabel;
    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private TextArea messageArea;

    private Stage stage;

    void setup(Stage stage, String title, String message, Type type) {
        this.stage = stage;
        titleLabel.setText(title);
        messageArea.setText(message);
        switch (type) {
            case ERROR -> {
                headerBar.getStyleClass().setAll("dialog-header-error");
                iconLabel.setText("✕");
                subtitleLabel.setText("Please review and correct the issue.");
            }
            case INFO -> {
                headerBar.getStyleClass().setAll("dialog-header-info");
                iconLabel.setText("i");
                subtitleLabel.setText("");
            }
        }
    }

    @FXML
    public void handleClose() {
        stage.close();
    }

    private static void show(String title, String message, Type type) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    MessageDialogViewController.class.getResource("/org/rac/gui/MessageDialogView.fxml"));
            Parent root = loader.load();
            MessageDialogViewController controller = loader.getController();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setResizable(false);
            stage.setScene(new Scene(root));

            controller.setup(stage, title, message, type);
            stage.showAndWait();
        } catch (IOException e) {
            // FXML is bundled in resources — this should never happen
            throw new RuntimeException("Failed to load MessageDialogView.fxml", e);
        }
    }

    public static void showError(String title, String message) {
        show(title, message, Type.ERROR);
    }

    public static void showInfo(String title, String message) {
        show(title, message, Type.INFO);
    }

    private enum Type { ERROR, INFO }
}
