package org.rac.gui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.rac.Main;
import org.rac.services.WamidStatusService;
import org.rac.services.WhatsAppApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SendTestMessageViewController {

    private static final Logger logger = LoggerFactory.getLogger(SendTestMessageViewController.class);
    private static final DateTimeFormatter TS_FMT   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FMT  = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int AUTO_REFRESH_SECONDS = 30;

    @FXML private TextField phoneField;
    @FXML private Button    sendButton;
    @FXML private Label     sendStatusLabel;

    @FXML private Label    trackingStatusLabel;
    @FXML private CheckBox autoRefreshCheck;
    @FXML private Button   refreshButton;

    @FXML private TableView<WamidStatusRow>            resultTable;
    @FXML private TableColumn<WamidStatusRow, String>  colWamid;
    @FXML private TableColumn<WamidStatusRow, String>  colRecipient;
    @FXML private TableColumn<WamidStatusRow, String>  colStatus;
    @FXML private TableColumn<WamidStatusRow, String>  colSentAt;
    @FXML private TableColumn<WamidStatusRow, String>  colDeliveredAt;
    @FXML private TableColumn<WamidStatusRow, String>  colReadAt;
    @FXML private TableColumn<WamidStatusRow, String>  colError;

    @FXML private Label wsTotal;
    @FXML private Label wsSent;
    @FXML private Label wsDelivered;
    @FXML private Label wsRead;
    @FXML private Label wsFailed;
    @FXML private Label wsPending;

    private final WhatsAppApiService whatsAppApiService = new WhatsAppApiService();
    private final WamidStatusService wamidStatusService = new WamidStatusService();
    private final ObservableList<WamidStatusRow> rows = FXCollections.observableArrayList();
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private String currentWamid;

    @FXML
    public void initialize() {
        resultTable.setItems(rows);
        colWamid.setCellValueFactory(cd -> cd.getValue().wamid);
        colRecipient.setCellValueFactory(cd -> cd.getValue().recipientId);
        colStatus.setCellValueFactory(cd -> cd.getValue().status);
        colSentAt.setCellValueFactory(cd -> cd.getValue().sentAt);
        colDeliveredAt.setCellValueFactory(cd -> cd.getValue().deliveredAt);
        colReadAt.setCellValueFactory(cd -> cd.getValue().readAt);
        colError.setCellValueFactory(cd -> cd.getValue().error);

        resultTable.setRowFactory(tv -> {
            TableRow<WamidStatusRow> row = new TableRow<>();
            MenuItem copyStatus = new MenuItem("Copy Status");
            copyStatus.setOnAction(e -> {
                WamidStatusRow item = row.getItem();
                if (item != null) {
                    ClipboardContent cc = new ClipboardContent();
                    cc.putString(item.status.get() != null ? item.status.get() : "");
                    Clipboard.getSystemClipboard().setContent(cc);
                }
            });
            MenuItem copyWamid = new MenuItem("Copy WAMID");
            copyWamid.setOnAction(e -> {
                WamidStatusRow item = row.getItem();
                if (item != null) {
                    ClipboardContent cc = new ClipboardContent();
                    cc.putString(item.wamid.get() != null ? item.wamid.get() : "");
                    Clipboard.getSystemClipboard().setContent(cc);
                }
            });
            ContextMenu menu = new ContextMenu(copyStatus, copyWamid);
            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(menu)
            );
            return row;
        });

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setText(null); setStyle(""); return; }
                setText(status);
                String lower = status.toLowerCase();
                if (lower.startsWith("read"))       setStyle("-fx-text-fill: #166534; -fx-font-weight: bold;");
                else if (lower.startsWith("del"))   setStyle("-fx-text-fill: #1d4ed8; -fx-font-weight: bold;");
                else if (lower.startsWith("fail"))  setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                else if (lower.equals("pending"))   setStyle("-fx-text-fill: #94a3b8;");
                else                                setStyle("-fx-text-fill: #92400e;");
            }
        });

        refreshButton.setDisable(true);
        autoRefreshCheck.setDisable(true);

        MenuItem copyMsg = new MenuItem("Copy");
        copyMsg.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(trackingStatusLabel.getText() != null ? trackingStatusLabel.getText() : "");
            Clipboard.getSystemClipboard().setContent(cc);
        });
        trackingStatusLabel.setContextMenu(new ContextMenu(copyMsg));
    }

    @FXML
    public void handleSend() {
        String phone = phoneField.getText();
        if (phone == null || phone.isBlank()) {
            sendStatusLabel.setText("Please enter a phone number.");
            return;
        }
        phone = phone.trim();

        sendButton.setDisable(true);
        sendStatusLabel.setText("");
        rows.clear();
        resetSummary();
        currentWamid = null;
        refreshButton.setDisable(true);
        autoRefreshCheck.setDisable(true);
        trackingStatusLabel.setText("Send a message above to start tracking.");

        final String toPhone = phone;
        new Thread(() -> {
            try {
                String wamid = whatsAppApiService.sendTestMessage(toPhone);
                Platform.runLater(() -> {
                    currentWamid = wamid;
                    sendStatusLabel.setText("Sent! WAMID: " + wamid);
                    WamidStatusRow row = new WamidStatusRow();
                    row.wamid.set(wamid);
                    row.recipientId.set(toPhone);
                    row.status.set("Sent");
                    row.sentAt.set("—");
                    row.deliveredAt.set("—");
                    row.readAt.set("—");
                    row.error.set("—");
                    rows.setAll(row);
                    updateSummary();
                    refreshButton.setDisable(false);
                    autoRefreshCheck.setDisable(false);
                    trackingStatusLabel.setText("Message sent. Click Refresh to check delivery status.");
                    sendButton.setDisable(false);
                });
            } catch (Exception e) {
                logger.error("Failed to send test message to {}", toPhone, e);
                Platform.runLater(() -> {
                    sendStatusLabel.setText("Error: " + e.getMessage());
                    sendButton.setDisable(false);
                });
            }
        }, "send-test-message-thread").start();
    }

    @FXML
    public void handleRefresh() {
        if (currentWamid == null || refreshing.getAndSet(true)) return;
        trackingStatusLabel.setText("Checking status…");

        new Thread(() -> {
            try {
                Map<String, WamidStatusService.WamidStatus> results =
                        wamidStatusService.checkBatch(List.of(currentWamid));
                WamidStatusService.WamidStatus ws = results.get(currentWamid);
                Platform.runLater(() -> {
                    if (!rows.isEmpty()) {
                        WamidStatusRow row = rows.get(0);
                        if (ws != null) {
                            row.status.set(formatStatusDisplay(ws));
                            if (ws.recipientId() != null) row.recipientId.set(ws.recipientId());
                            row.sentAt.set(formatTimestamp(ws.sentAt()));
                            row.deliveredAt.set(formatTimestamp(ws.deliveredAt()));
                            row.readAt.set(formatTimestamp(ws.readAt()));
                            String err = "";
                            if (ws.errorCode() != null) err += ws.errorCode();
                            if (ws.errorTitle() != null) err += (err.isEmpty() ? "" : " — ") + ws.errorTitle();
                            row.error.set(err.isEmpty() ? "—" : err);
                        } else {
                            row.status.set("Not Found");
                        }
                    }
                    updateSummary();
                    trackingStatusLabel.setText("Last checked at " + LocalTime.now().format(TIME_FMT));
                    refreshing.set(false);
                });
            } catch (Exception e) {
                logger.error("Status check failed", e);
                Platform.runLater(() -> {
                    trackingStatusLabel.setText("Refresh failed: " + e.getMessage());
                    refreshing.set(false);
                });
            }
        }, "test-msg-status-check").start();
    }

    @FXML
    public void handleAutoRefreshToggle() {
        if (autoRefreshCheck.isSelected()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "test-msg-auto-refresh");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(
                    () -> Platform.runLater(this::handleRefresh),
                    AUTO_REFRESH_SECONDS, AUTO_REFRESH_SECONDS, TimeUnit.SECONDS);
            trackingStatusLabel.setText("Auto-refresh enabled (every " + AUTO_REFRESH_SECONDS + " s)");
        } else {
            stopAutoRefresh();
            trackingStatusLabel.setText("Auto-refresh disabled.");
        }
    }

    @FXML
    public void handleBackToHome() {
        stopAutoRefresh();
        try { Main.showMainView(); }
        catch (IOException e) { logger.error("Failed to navigate home", e); }
    }

    private void stopAutoRefresh() {
        if (scheduler != null && !scheduler.isShutdown()) scheduler.shutdownNow();
    }

    private void updateSummary() {
        int total = 0, sent = 0, delivered = 0, read = 0, failed = 0, pending = 0;
        for (WamidStatusRow r : rows) {
            String s = r.status.get() != null ? r.status.get().toLowerCase() : "";
            total++;
            if (s.startsWith("read"))                           read++;
            else if (s.startsWith("deliv"))                     delivered++;
            else if (s.startsWith("fail"))                      failed++;
            else if (s.equals("sent") || s.equals("accepted"))  sent++;
            else if (s.equals("pending"))                       pending++;
        }
        wsTotal.setText("Total: " + total);
        wsSent.setText("Sent: " + sent);
        wsDelivered.setText("Delivered: " + delivered);
        wsRead.setText("Read: " + read);
        wsFailed.setText("Failed: " + failed);
        wsPending.setText("Pending: " + pending);
    }

    private void resetSummary() {
        wsTotal.setText("Total: 0");
        wsSent.setText("Sent: 0");
        wsDelivered.setText("Delivered: 0");
        wsRead.setText("Read: 0");
        wsFailed.setText("Failed: 0");
        wsPending.setText("Pending: 0");
    }

    private static String formatTimestamp(Long epoch) {
        if (epoch == null) return "—";
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault());
        return ldt.format(TS_FMT);
    }

    private static String formatStatusDisplay(WamidStatusService.WamidStatus ws) {
        return switch (ws.status().toLowerCase()) {
            case "sent"      -> "Sent";
            case "delivered" -> "Delivered ✓";
            case "read"      -> "Read ✓✓";
            case "failed"    -> ws.errorTitle() != null ? "Failed: " + ws.errorTitle() : "Failed ✗";
            case "deleted"   -> "Deleted";
            case "pending"   -> "Pending";
            default          -> ws.status();
        };
    }

    public static class WamidStatusRow {
        final StringProperty wamid       = new SimpleStringProperty();
        final StringProperty recipientId = new SimpleStringProperty();
        final StringProperty status      = new SimpleStringProperty();
        final StringProperty sentAt      = new SimpleStringProperty();
        final StringProperty deliveredAt = new SimpleStringProperty();
        final StringProperty readAt      = new SimpleStringProperty();
        final StringProperty error       = new SimpleStringProperty();
    }
}
