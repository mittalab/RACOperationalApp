package org.rac.gui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;

import javafx.stage.Modality;
import javafx.stage.Stage;
import org.rac.model.MessageDelivery;
import org.rac.services.WhatsAppApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DeliveryTrackerViewController {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryTrackerViewController.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int AUTO_REFRESH_SECONDS = 30;

    // Status values returned by WhatsApp (normalised to lowercase for comparison)
    private static final String STATUS_ACCEPTED  = "accepted";
    private static final String STATUS_SENT      = "sent";
    private static final String STATUS_DELIVERED = "delivered";
    private static final String STATUS_READ      = "read";
    private static final String STATUS_FAILED    = "failed";

    @FXML private TableView<MessageDelivery>  deliveryTable;
    @FXML private TableColumn<MessageDelivery, String> nameCol;
    @FXML private TableColumn<MessageDelivery, String> phoneCol;
    @FXML private TableColumn<MessageDelivery, String> statusCol;
    @FXML private TableColumn<MessageDelivery, String> lastCheckedCol;
    @FXML private CheckBox autoRefreshCheckbox;
    @FXML private Label    statusLabel;

    private Stage stage;
    private final WhatsAppApiService whatsAppApiService = new WhatsAppApiService();
    private final ObservableList<MessageDelivery> records = FXCollections.observableArrayList();
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    @FXML
    public void initialize() {
        deliveryTable.setItems(records);

        nameCol.setCellValueFactory(cd -> cd.getValue().studentNameProperty());
        phoneCol.setCellValueFactory(cd -> cd.getValue().phoneProperty());
        statusCol.setCellValueFactory(cd -> cd.getValue().statusProperty());
        lastCheckedCol.setCellValueFactory(cd -> cd.getValue().lastCheckedProperty());

        // Colour-coded status cell
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(status);
                String lower = status.toLowerCase();
                switch (lower) {
                    case STATUS_READ:
                        setStyle("-fx-text-fill: #166534; -fx-font-weight: bold;");
                        break;
                    case STATUS_DELIVERED:
                        setStyle("-fx-text-fill: #1d4ed8; -fx-font-weight: bold;");
                        break;
                    case STATUS_SENT:
                    case STATUS_ACCEPTED:
                        setStyle("-fx-text-fill: #92400e;");
                        break;
                    case STATUS_FAILED:
                        setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                        break;
                    default:
                        setStyle("-fx-text-fill: #64748b;");
                }
            }
        });
    }

    void setup(Stage stage, List<MessageDelivery> deliveryRecords) {
        this.stage = stage;
        records.setAll(deliveryRecords);
        stage.setOnHidden(e -> stopAutoRefresh());
    }

    @FXML
    public void handleRefresh() {
        if (refreshing.getAndSet(true)) return; // prevent concurrent refreshes
        updateStatusLabel("Checking delivery statuses…");

        new Thread(() -> {
            int checked = 0;
            for (MessageDelivery record : records) {
                String msgId = record.getMessageId();
                if (msgId == null || msgId.isEmpty()) {
                    Platform.runLater(() -> record.setStatus("No ID"));
                    continue;
                }
                try {
                    String rawStatus = whatsAppApiService.checkMessageStatus(msgId);
                    String display = formatStatus(rawStatus);
                    String now = LocalTime.now().format(TIME_FMT);
                    Platform.runLater(() -> {
                        record.setStatus(display);
                        record.setLastChecked(now);
                    });
                    checked++;
                } catch (Exception e) {
                    logger.warn("Could not check status for {}: {}", record.getStudentName(), e.getMessage());
                    Platform.runLater(() -> record.setStatus("Check failed"));
                }
            }
            final int finalChecked = checked;
            Platform.runLater(() -> {
                updateStatusLabel("Last refreshed at " + LocalTime.now().format(TIME_FMT)
                        + " — " + finalChecked + " checked");
                refreshing.set(false);
            });
        }, "delivery-status-checker").start();
    }

    @FXML
    public void handleAutoRefreshToggle() {
        if (autoRefreshCheckbox.isSelected()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "delivery-auto-refresh");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(
                    () -> Platform.runLater(this::handleRefresh),
                    AUTO_REFRESH_SECONDS, AUTO_REFRESH_SECONDS, TimeUnit.SECONDS);
            updateStatusLabel("Auto-refresh enabled (every " + AUTO_REFRESH_SECONDS + " s)");
        } else {
            stopAutoRefresh();
            updateStatusLabel("Auto-refresh disabled");
        }
    }

    @FXML
    public void handleClose() {
        stopAutoRefresh();
        stage.close();
    }

    private void stopAutoRefresh() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    private void updateStatusLabel(String text) {
        Platform.runLater(() -> statusLabel.setText(text));
    }

    /** Maps WhatsApp raw status string to a user-friendly display label. */
    private String formatStatus(String raw) {
        if (raw == null) return "Unknown";
        switch (raw.toLowerCase()) {
            case STATUS_ACCEPTED:  return "Accepted";
            case STATUS_SENT:      return "Sent";
            case STATUS_DELIVERED: return "Delivered ✓";
            case STATUS_READ:      return "Read ✓✓";
            case STATUS_FAILED:    return "Failed ✗";
            default:               return capitalize(raw);
        }
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static void show(List<MessageDelivery> records) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                DeliveryTrackerViewController.class.getResource(
                        "/org/rac/gui/DeliveryTrackerView.fxml"));
        Parent root = loader.load();
        DeliveryTrackerViewController controller = loader.getController();

        Stage stage = new Stage();
        stage.setTitle("WhatsApp Delivery Tracker");
        stage.initModality(Modality.NONE); // non-blocking — user can keep using the app
        stage.setResizable(true);
        stage.setScene(new Scene(root));

        controller.setup(stage, records);
        stage.show();
    }
}
