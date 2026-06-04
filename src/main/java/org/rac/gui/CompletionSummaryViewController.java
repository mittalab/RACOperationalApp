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

    void setup(Stage stage, int parentSuccess, int parentTotal, boolean waEnabled,
               int adminSuccess, int adminTotal,
               boolean quotaExceeded, String quotaStudentName,
               List<String> errors, List<MessageDelivery> deliveryRecords) {
        this.stage = stage;
        this.deliveryRecords = deliveryRecords;

        StringBuilder summary = new StringBuilder();
        if (waEnabled && parentTotal > 0) {
            summary.append("Parents: ").append(parentSuccess).append(" / ").append(parentTotal)
                   .append(" messages sent");
            if (quotaExceeded) {
                summary.append("\n\nWhatsApp sending stopped at student '")
                       .append(quotaStudentName)
                       .append("' due to daily/tier quota exceeded.")
                       .append("\nImages for all students saved in the output folder.");
            }
        } else if (!waEnabled) {
            summary.append("Images generated for ").append(parentTotal).append(" students.");
        }
        if (adminSuccess > 0) {
            if (summary.length() > 0) summary.append("\n");
            summary.append("Admin: ").append(adminSuccess).append(" / ").append(adminTotal).append(" message(s) sent");
        }
        summaryLabel.setText(summary.toString());

        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String err : errors) sb.append("• ").append(err).append("\n");
            errorsArea.setText(sb.toString().stripTrailing());
            errorsHeading.setVisible(true);
            errorsHeading.setManaged(true);
        }

        boolean showTracker = deliveryRecords != null && !deliveryRecords.isEmpty();
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

    public static void show(int parentSuccess, int parentTotal, boolean waEnabled,
                            int adminSuccess, int adminTotal,
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

        controller.setup(stage, parentSuccess, parentTotal, waEnabled, adminSuccess, adminTotal,
                quotaExceeded, quotaStudentName, errors, deliveryRecords);
        stage.showAndWait();
    }
}
