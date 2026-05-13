package org.rac.gui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

public class ConfirmationViewController {

    @FXML private Label studentCountLabel;
    @FXML private Label whatsAppLabel;

    private Stage stage;
    private boolean confirmed = false;

    void setup(Stage stage, int count, boolean sendWA) {
        this.stage = stage;
        studentCountLabel.setText(count + " student(s) will be processed.");
        whatsAppLabel.setText(sendWA ? "Yes — results will be sent on WhatsApp" : "No — images only, no WhatsApp messages");
        whatsAppLabel.getStyleClass().setAll(sendWA ? "dialog-wa-yes-label" : "dialog-wa-no-label");
    }

    @FXML
    public void handleOk() {
        confirmed = true;
        stage.close();
    }

    @FXML
    public void handleCancel() {
        stage.close();
    }

    public static boolean show(int count, boolean sendWA) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                ConfirmationViewController.class.getResource("/org/rac/gui/ConfirmationView.fxml"));
        Parent root = loader.load();
        ConfirmationViewController controller = loader.getController();

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Confirmation");
        stage.setResizable(false);
        stage.setScene(new Scene(root));

        controller.setup(stage, count, sendWA);
        stage.showAndWait();
        return controller.confirmed;
    }
}
