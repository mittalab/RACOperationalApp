package org.rac.gui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.rac.model.MessageDelivery;
import org.rac.services.WamidStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class DeliveryTrackerViewController {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryTrackerViewController.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int AUTO_REFRESH_SECONDS = 30;

    @FXML private TableView<MessageDelivery>  deliveryTable;
    @FXML private TableColumn<MessageDelivery, String> nameCol;
    @FXML private TableColumn<MessageDelivery, String> phoneCol;
    @FXML private TableColumn<MessageDelivery, String> wamidCol;
    @FXML private TableColumn<MessageDelivery, String> statusCol;
    @FXML private TableColumn<MessageDelivery, String> lastCheckedCol;
    @FXML private CheckBox autoRefreshCheckbox;
    @FXML private Label    statusLabel;
    @FXML private Label    sumTotal;
    @FXML private Label    sumSent;
    @FXML private Label    sumDelivered;
    @FXML private Label    sumRead;
    @FXML private Label    sumFailed;
    @FXML private Label    sumPending;

    private Stage stage;
    private final WamidStatusService wamidStatusService = new WamidStatusService();
    private final ObservableList<MessageDelivery> records = FXCollections.observableArrayList();
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    @FXML
    public void initialize() {
        deliveryTable.setItems(records);

        nameCol.setCellValueFactory(cd -> cd.getValue().studentNameProperty());
        phoneCol.setCellValueFactory(cd -> cd.getValue().phoneProperty());
        wamidCol.setCellValueFactory(cd -> cd.getValue().messageIdProperty());
        statusCol.setCellValueFactory(cd -> cd.getValue().statusProperty());
        lastCheckedCol.setCellValueFactory(cd -> cd.getValue().lastCheckedProperty());

        // Right-click context menu on rows for copying WAMIDs
        deliveryTable.setRowFactory(tv -> {
            TableRow<MessageDelivery> row = new TableRow<>();
            MenuItem copyWamid = new MenuItem("Copy WAMID");
            copyWamid.setOnAction(e -> {
                MessageDelivery item = row.getItem();
                if (item != null) {
                    ClipboardContent cc = new ClipboardContent();
                    cc.putString(item.getMessageId() != null ? item.getMessageId() : "");
                    Clipboard.getSystemClipboard().setContent(cc);
                }
            });
            MenuItem copyAllWamids = new MenuItem("Copy all WAMIDs (comma-separated)");
            copyAllWamids.setOnAction(e -> {
                String joined = records.stream()
                        .map(MessageDelivery::getMessageId)
                        .filter(id -> id != null && !id.isEmpty())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                ClipboardContent cc = new ClipboardContent();
                cc.putString(joined);
                Clipboard.getSystemClipboard().setContent(cc);
            });
            ContextMenu menu = new ContextMenu(copyWamid, copyAllWamids);
            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(menu)
            );
            return row;
        });

        // Colour-coded status cell
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setText(null); setStyle(""); return; }
                setText(status);
                String lower = status.toLowerCase();
                if (lower.equals("read ✓✓") || lower.startsWith("read")) {
                    setStyle("-fx-text-fill: #166534; -fx-font-weight: bold;");
                } else if (lower.startsWith("delivered")) {
                    setStyle("-fx-text-fill: #1d4ed8; -fx-font-weight: bold;");
                } else if (lower.equals("sent") || lower.equals("accepted")) {
                    setStyle("-fx-text-fill: #92400e;");
                } else if (lower.startsWith("failed") || lower.startsWith("no id")) {
                    setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                } else {
                    setStyle("-fx-text-fill: #64748b;");
                }
            }
        });
    }

    void setup(Stage stage, List<MessageDelivery> deliveryRecords) {
        this.stage = stage;
        records.setAll(deliveryRecords);
        stage.setOnHidden(e -> stopAutoRefresh());
        updateSummary();
        handleRefresh();
    }

    @FXML
    public void handleRefresh() {
        if (refreshing.getAndSet(true)) return;
        updateStatusLabel("Checking delivery statuses…");

        List<String> wamids = records.stream()
                .map(MessageDelivery::getMessageId)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toList());

        if (wamids.isEmpty()) {
            updateStatusLabel("No message IDs to check.");
            refreshing.set(false);
            return;
        }

        new Thread(() -> {
            try {
                Map<String, WamidStatusService.WamidStatus> results = wamidStatusService.checkBatch(wamids);
                String now = LocalTime.now().format(TIME_FMT);
                int checked = 0;
                for (MessageDelivery record : records) {
                    String msgId = record.getMessageId();
                    if (msgId == null || msgId.isEmpty()) {
                        Platform.runLater(() -> record.setStatus("No ID"));
                        continue;
                    }
                    WamidStatusService.WamidStatus ws = results.get(msgId);
                    String display = formatStatus(ws);
                    Platform.runLater(() -> {
                        record.setStatus(display);
                        record.setLastChecked(now);
                    });
                    checked++;
                }
                final int finalChecked = checked;
                Platform.runLater(() -> {
                    updateStatusLabel("Last refreshed at " + LocalTime.now().format(TIME_FMT)
                            + " — " + finalChecked + " checked");
                    updateSummary();
                    refreshing.set(false);
                });
            } catch (Exception e) {
                logger.warn("Batch status check failed: {}", e.getMessage());
                Platform.runLater(() -> {
                    updateStatusLabel("Refresh failed: " + e.getMessage());
                    updateSummary();
                    refreshing.set(false);
                });
            }
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
        if (scheduler != null && !scheduler.isShutdown()) scheduler.shutdownNow();
    }

    private void updateStatusLabel(String text) {
        Platform.runLater(() -> statusLabel.setText(text));
    }

    private void updateSummary() {
        int total = 0, sent = 0, delivered = 0, read = 0, failed = 0, pending = 0;
        for (MessageDelivery r : records) {
            String s = r.getStatus() != null ? r.getStatus().toLowerCase() : "";
            total++;
            if (s.startsWith("read"))                          read++;
            else if (s.startsWith("deliv"))                    delivered++;
            else if (s.startsWith("fail") || s.startsWith("no id"))                     failed++;
            else if (s.equals("sent") || s.equals("accepted")) sent++;
            else if (s.equals("pending"))                      pending++;
        }
        final int t = total, se = sent, d = delivered, r = read, f = failed, p = pending;
        Platform.runLater(() -> {
            sumTotal.setText("Total: " + t);
            sumSent.setText("Sent: " + se);
            sumDelivered.setText("Delivered: " + d);
            sumRead.setText("Read: " + r);
            sumFailed.setText("Failed: " + f);
            sumPending.setText("Pending: " + p);
        });
    }

    private String formatStatus(WamidStatusService.WamidStatus ws) {
        if (ws == null) return "Unknown";
        return switch (ws.status().toLowerCase()) {
            case "accepted"  -> "Accepted";
            case "sent"      -> "Sent";
            case "delivered" -> "Delivered ✓";
            case "read"      -> "Read ✓✓";
            case "failed"    -> ws.errorTitle() != null ? "Failed: " + ws.errorTitle() : "Failed ✗";
            case "deleted"   -> "Deleted";
            case "pending"   -> "Pending";
            default          -> capitalize(ws.status());
        };
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
        stage.initModality(Modality.NONE);
        stage.setResizable(true);
        stage.setScene(new Scene(root));

        controller.setup(stage, records);
        stage.show();
    }
}
