package org.rac.gui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.rac.model.MessageDelivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class CompletionSummaryViewController {

    private static final Logger logger = LoggerFactory.getLogger(CompletionSummaryViewController.class);

    @FXML private Label    summaryLabel;
    @FXML private VBox     errorsHeading;
    @FXML private TextArea errorsArea;
    @FXML private Button   trackDeliveryButton;

    private Stage stage;
    private List<MessageDelivery> deliveryRecords;

    void setup(Stage stage, int successCount, int total, boolean waEnabled,
               boolean quotaExceeded, String quotaStudentName,
               List<String> errors, List<MessageDelivery> deliveryRecords) {
        this.stage = stage;
        this.deliveryRecords = deliveryRecords;

        StringBuilder summary = new StringBuilder();
        if (waEnabled) {
            summary.append("Messages sent: ").append(successCount).append(" / ").append(total);
            if (quotaExceeded) {
                summary.append("\n\nWhatsApp sending stopped at student '")
                       .append(quotaStudentName)
                       .append("' due to daily/tier quota exceeded.")
                       .append("\nImages for all students saved in the output folder.");
            }
        } else {
            summary.append("Images generated for ").append(total).append(" students.");
        }
        summaryLabel.setText(summary.toString());

        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String err : errors) sb.append("• ").append(err).append("\n");
            errorsArea.setText(sb.toString().stripTrailing());
            errorsHeading.setVisible(true);
            errorsHeading.setManaged(true);
        }

        boolean showTracker = waEnabled && deliveryRecords != null && !deliveryRecords.isEmpty();
        trackDeliveryButton.setVisible(showTracker);
        trackDeliveryButton.setManaged(showTracker);
    }

    @FXML
    public void handleTrackDelivery() {
        try {
            DeliveryTrackerViewController.show(deliveryRecords);
        } catch (IOException e) {
            logger.error("Failed to open delivery tracker", e);
        }
    }

    @FXML
    public void handleClose() {
        stage.close();
    }

    public static void show(int successCount, int total, boolean waEnabled,
                            boolean quotaExceeded, String quotaStudentName,
                            List<String> errors, List<MessageDelivery> deliveryRecords) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                CompletionSummaryViewController.class.getResource(
                        "/org/rac/gui/CompletionSummaryView.fxml"));
        Parent root = loader.load();
        CompletionSummaryViewController controller = loader.getController();

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Processing Complete");
        stage.setResizable(true);
        stage.setScene(new Scene(root));

        controller.setup(stage, successCount, total, waEnabled,
                quotaExceeded, quotaStudentName, errors, deliveryRecords);
        stage.showAndWait();
    }
}
