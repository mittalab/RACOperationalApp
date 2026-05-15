package org.rac.gui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.rac.Main;
import org.rac.services.WamidStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class CheckWamidStatusViewController {

    private static final Logger logger = LoggerFactory.getLogger(CheckWamidStatusViewController.class);
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML private TextArea wamidInput;
    @FXML private Button   checkButton;
    @FXML private Label    statusLabel;
    @FXML private TableView<WamidStatusRow> resultTable;
    @FXML private Label    wsTotal;
    @FXML private Label    wsSent;
    @FXML private Label    wsDelivered;
    @FXML private Label    wsRead;
    @FXML private Label    wsFailed;
    @FXML private Label    wsPending;
    @FXML private TableColumn<WamidStatusRow, String> colWamid;
    @FXML private TableColumn<WamidStatusRow, String> colRecipient;
    @FXML private TableColumn<WamidStatusRow, String> colStatus;
    @FXML private TableColumn<WamidStatusRow, String> colSentAt;
    @FXML private TableColumn<WamidStatusRow, String> colDeliveredAt;
    @FXML private TableColumn<WamidStatusRow, String> colReadAt;
    @FXML private TableColumn<WamidStatusRow, String> colError;

    private final WamidStatusService wamidStatusService = new WamidStatusService();
    private final ObservableList<WamidStatusRow> rows = FXCollections.observableArrayList();

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

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setText(null); setStyle(""); return; }
                setText(status);
                String lower = status.toLowerCase();
                if (lower.startsWith("read"))      setStyle("-fx-text-fill: #166534; -fx-font-weight: bold;");
                else if (lower.startsWith("del"))  setStyle("-fx-text-fill: #1d4ed8; -fx-font-weight: bold;");
                else if (lower.startsWith("fail")) setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                else if (lower.equals("pending"))  setStyle("-fx-text-fill: #94a3b8;");
                else                               setStyle("-fx-text-fill: #92400e;");
            }
        });
    }

    @FXML
    public void handleCheck() {
        String raw = wamidInput.getText();
        if (raw == null || raw.isBlank()) {
            statusLabel.setText("Please enter at least one WAMID.");
            return;
        }

        List<String> wamids = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (wamids.isEmpty()) {
            statusLabel.setText("No valid WAMIDs found.");
            return;
        }
        if (wamids.size() > 100) {
            statusLabel.setText("Maximum 100 WAMIDs per request.");
            return;
        }

        checkButton.setDisable(true);
        statusLabel.setText("Checking " + wamids.size() + " WAMID(s)…");
        rows.clear();
        resetSummary();

        new Thread(() -> {
            try {
                Map<String, WamidStatusService.WamidStatus> results = wamidStatusService.checkBatch(wamids);
                List<WamidStatusRow> newRows = new ArrayList<>();
                for (String wamid : wamids) {
                    WamidStatusService.WamidStatus ws = results.get(wamid);
                    newRows.add(WamidStatusRow.from(wamid, ws));
                }
                Platform.runLater(() -> {
                    rows.setAll(newRows);
                    statusLabel.setText("Done — " + results.size() + " result(s) returned.");
                    updateSummary();
                    checkButton.setDisable(false);
                });
            } catch (Exception e) {
                logger.error("Batch status check failed", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    checkButton.setDisable(false);
                });
            }
        }, "wamid-check-thread").start();
    }

    private void updateSummary() {
        int total = 0, sent = 0, delivered = 0, read = 0, failed = 0, pending = 0;
        for (WamidStatusRow r : rows) {
            String s = r.status.get() != null ? r.status.get().toLowerCase() : "";
            total++;
            if (s.startsWith("read"))                          read++;
            else if (s.startsWith("deliv"))                    delivered++;
            else if (s.startsWith("fail"))                     failed++;
            else if (s.equals("sent") || s.equals("accepted")) sent++;
            else if (s.equals("pending"))                      pending++;
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

    @FXML
    public void handleBackToHome() {
        try { Main.showMainView(); }
        catch (IOException e) { logger.error("Failed to navigate home", e); }
    }

    private static String formatTimestamp(Long epoch) {
        if (epoch == null) return "—";
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault());
        return ldt.format(TS_FMT);
    }

    public static class WamidStatusRow {
        final StringProperty wamid       = new SimpleStringProperty();
        final StringProperty recipientId = new SimpleStringProperty();
        final StringProperty status      = new SimpleStringProperty();
        final StringProperty sentAt      = new SimpleStringProperty();
        final StringProperty deliveredAt = new SimpleStringProperty();
        final StringProperty readAt      = new SimpleStringProperty();
        final StringProperty error       = new SimpleStringProperty();

        static WamidStatusRow from(String wamidStr, WamidStatusService.WamidStatus ws) {
            WamidStatusRow row = new WamidStatusRow();
            row.wamid.set(wamidStr);
            if (ws == null) {
                row.status.set("Not Found");
                row.recipientId.set("—");
                row.sentAt.set("—"); row.deliveredAt.set("—"); row.readAt.set("—"); row.error.set("—");
                return row;
            }
            row.status.set(formatStatusDisplay(ws));
            row.recipientId.set(ws.recipientId() != null ? ws.recipientId() : "—");
            row.sentAt.set(formatTimestamp(ws.sentAt()));
            row.deliveredAt.set(formatTimestamp(ws.deliveredAt()));
            row.readAt.set(formatTimestamp(ws.readAt()));
            String err = "";
            if (ws.errorCode() != null) err += ws.errorCode();
            if (ws.errorTitle() != null) err += (err.isEmpty() ? "" : " — ") + ws.errorTitle();
            row.error.set(err.isEmpty() ? "—" : err);
            return row;
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
    }
}
