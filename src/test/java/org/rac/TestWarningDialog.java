package org.rac;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.rac.gui.MessageDialogViewController;

public class TestWarningDialog extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        Platform.runLater(() -> {
            MessageDialogViewController.showWarning(
                "Warning: Google Drive Upload Failed",
                "Please inform Abhishek that the Google Drive uploads are failing.\n\nError details: Simulated connection timeout to Google API."
            );
            Platform.exit();
            System.exit(0);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
