package org.rac.gui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.rac.Main;
import org.rac.model.MessageDelivery;
import org.rac.services.GoogleDriveService;
import org.rac.services.WamidStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class RetrieveRunStatusViewController {

    private static final Logger logger = LoggerFactory.getLogger(RetrieveRunStatusViewController.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int AUTO_REFRESH_SECONDS = 30;

    @FXML private RadioButton localRadio;
    @FXML private RadioButton driveRadio;
    @FXML private ToggleGroup folderSourceGroup;
    @FXML private HBox localInputBox;
    @FXML private HBox driveInputBox;
    @FXML private Label localFolderLabel;
    @FXML private TextField driveUrlField;
    @FXML private Button loadButton;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Label statusLabel;

    @FXML private TableView<MessageDelivery> deliveryTable;
    @FXML private TableColumn<MessageDelivery, String> nameCol;
    @FXML private TableColumn<MessageDelivery, String> phoneCol;
    @FXML private TableColumn<MessageDelivery, String> wamidCol;
    @FXML private TableColumn<MessageDelivery, String> statusCol;
    @FXML private TableColumn<MessageDelivery, String> lastCheckedCol;

    @FXML private Label sumTotal;
    @FXML private Label sumSent;
    @FXML private Label sumDelivered;
    @FXML private Label sumRead;
    @FXML private Label sumFailed;
    @FXML private Label sumPending;

    @FXML private CheckBox autoRefreshCheckbox;
    @FXML private Label refreshLabel;
    @FXML private Button refreshButton;

    private File localFolder;
    private final GoogleDriveService googleDriveService = new GoogleDriveService();
    private final WamidStatusService wamidStatusService = new WamidStatusService();
    private final ObservableList<MessageDelivery> records = FXCollections.observableArrayList();
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    @FXML
    public void initialize() {
        // Toggle visibility of input fields depending on RadioButton selection
        localRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            localInputBox.setVisible(newVal);
            localInputBox.setManaged(newVal);
        });
        driveRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            driveInputBox.setVisible(newVal);
            driveInputBox.setManaged(newVal);
        });

        // Initialize Table
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
                    Bindings.when(row.emptyProperty())
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

    @FXML
    public void handleBrowseLocalFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Run Output Folder");
        Stage stage = (Stage) localFolderLabel.getScene().getWindow();
        File selected = chooser.showDialog(stage);
        if (selected != null) {
            localFolder = selected;
            localFolderLabel.setText(selected.getName());
            statusLabel.setText("Local folder selected: " + selected.getName());
        }
    }

    @FXML
    public void handleLoadRun() {
        // Validation
        if (localRadio.isSelected()) {
            if (localFolder == null) {
                MessageDialogViewController.showError("Missing Input", "Please select a local folder first.");
                return;
            }
        } else {
            String url = driveUrlField.getText();
            if (url == null || url.isBlank()) {
                MessageDialogViewController.showError("Missing Input", "Please enter a Google Drive folder URL.");
                return;
            }
        }

        // Toggle Loading UI
        setLoadingState(true);
        records.clear();
        updateSummary();
        statusLabel.setText("Loading run report...");

        boolean isLocal = localRadio.isSelected();
        String driveUrl = driveUrlField.getText();

        new Thread(() -> {
            File reportFile = null;
            File tempDriveFile = null;

            try {
                if (isLocal) {
                    reportFile = new File(localFolder, "run_report.xlsx");
                    if (!reportFile.exists()) {
                        throw new FileNotFoundException("The selected local folder does not contain 'run_report.xlsx'. Please select a valid run backup folder.");
                    }
                } else {
                    String folderId = extractFolderId(driveUrl);
                    if (folderId == null) {
                        throw new IllegalArgumentException("Could not extract a valid folder ID from the Google Drive URL. Ensure it matches the format: https://drive.google.com/drive/folders/<folderId>");
                    }

                    tempDriveFile = Files.createTempFile("drive_run_report_", ".xlsx").toFile();
                    tempDriveFile.deleteOnExit();

                    try {
                        googleDriveService.downloadFileFromFolder(folderId, "run_report.xlsx", tempDriveFile);
                        reportFile = tempDriveFile;
                    } catch (FileNotFoundException fe) {
                        throw new FileNotFoundException("The file 'run_report.xlsx' was not found in the Google Drive folder. Please ensure this is a valid run folder.");
                    }
                }

                // Read run report
                List<MessageDelivery> loadedRecords = readRunReport(reportFile);

                Platform.runLater(() -> {
                    records.setAll(loadedRecords);
                    statusLabel.setText("Loaded " + records.size() + " messages. Checking statuses...");
                    refreshButton.setDisable(false);
                    setLoadingState(false);
                    // Automatically trigger first status refresh
                    handleRefresh();
                });

            } catch (Exception e) {
                logger.error("Failed to load run report", e);
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.toString();
                Platform.runLater(() -> {
                    MessageDialogViewController.showError("Failed to Load Run", errorMsg);
                    statusLabel.setText("Error loading run data.");
                    setLoadingState(false);
                });
            } finally {
                if (tempDriveFile != null && tempDriveFile.exists()) {
                    tempDriveFile.delete();
                }
            }
        }, "run-report-loader").start();
    }

    private List<MessageDelivery> readRunReport(File file) throws IOException {
        List<MessageDelivery> list = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheet("Run Report");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            int rowCount = sheet.getLastRowNum();
            for (int i = 1; i <= rowCount; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String name = getCellStringValue(row.getCell(0));
                String phone = getCellStringValue(row.getCell(1));
                String wamid = getCellStringValue(row.getCell(3));

                if (name.isBlank() && phone.isBlank() && wamid.isBlank()) {
                    continue; // Skip empty rows
                }

                String initialStatus = wamid.isEmpty() ? "No ID" : "Pending";
                MessageDelivery delivery = new MessageDelivery(name, phone, wamid, initialStatus);
                list.add(delivery);
            }
        }
        return list;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue()).trim();
            case FORMULA:
                try {
                    return String.valueOf((long) cell.getNumericCellValue()).trim();
                } catch (Exception e) {
                    return cell.getStringCellValue().trim();
                }
            default:
                return "";
        }
    }

    private String extractFolderId(String url) {
        if (url == null || url.isBlank()) return null;
        String token = "folders/";
        int idx = url.indexOf(token);
        if (idx != -1) {
            String sub = url.substring(idx + token.length());
            int qIdx = sub.indexOf('?');
            if (qIdx != -1) {
                sub = sub.substring(0, qIdx);
            }
            int sIdx = sub.indexOf('/');
            if (sIdx != -1) {
                sub = sub.substring(0, sIdx);
            }
            return sub.trim();
        }
        // Fallback: Check if URL looks like raw ID
        if (url.matches("^[a-zA-Z0-9_-]{25,}$")) {
            return url.trim();
        }
        return null;
    }

    @FXML
    public void handleRefresh() {
        if (refreshing.getAndSet(true)) return;
        updateRefreshLabel("Checking statuses…");

        List<String> wamids = records.stream()
                .map(MessageDelivery::getMessageId)
                .filter(id -> id != null && !id.isEmpty())
                .collect(Collectors.toList());

        if (wamids.isEmpty()) {
            updateRefreshLabel("No message IDs to check.");
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
                    updateRefreshLabel("Last checked at " + LocalTime.now().format(TIME_FMT)
                            + " — " + finalChecked + " checked");
                    updateSummary();
                    refreshing.set(false);
                });
            } catch (Exception e) {
                logger.warn("Batch status check failed: {}", e.getMessage());
                Platform.runLater(() -> {
                    updateRefreshLabel("Refresh failed: " + e.getMessage());
                    updateSummary();
                    refreshing.set(false);
                });
            }
        }, "run-status-checker").start();
    }

    @FXML
    public void handleAutoRefreshToggle() {
        if (autoRefreshCheckbox.isSelected()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "run-auto-refresh");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(
                    () -> Platform.runLater(this::handleRefresh),
                    AUTO_REFRESH_SECONDS, AUTO_REFRESH_SECONDS, TimeUnit.SECONDS);
            updateRefreshLabel("Auto-refresh enabled (every " + AUTO_REFRESH_SECONDS + " s)");
        } else {
            stopAutoRefresh();
            updateRefreshLabel("Auto-refresh disabled");
        }
    }

    @FXML
    public void handleBackToHome() {
        stopAutoRefresh();
        try {
            Main.showMainView();
        } catch (IOException e) {
            logger.error("Failed to navigate home", e);
        }
    }

    private void stopAutoRefresh() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    private void updateRefreshLabel(String text) {
        Platform.runLater(() -> refreshLabel.setText(text));
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

    private void setLoadingState(boolean isLoading) {
        Platform.runLater(() -> {
            loadingIndicator.setVisible(isLoading);
            loadButton.setDisable(isLoading);
            localRadio.setDisable(isLoading);
            driveRadio.setDisable(isLoading);
            driveUrlField.setDisable(isLoading);
        });
    }
}
