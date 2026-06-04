package org.rac.gui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import org.rac.Main;
import org.rac.model.MessageDelivery;
import org.rac.model.Student;
import org.rac.services.ExcelReaderService;
import org.rac.services.ExcelWriterService;
import org.rac.services.GoogleDriveService;
import org.rac.services.ResultImageService;
import org.rac.services.WhatsAppApiService;
import org.rac.utils.MachineIdentifier;
import org.rac.utils.RunLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SendAdminNotificationsViewController {

    private static final Logger logger = LoggerFactory.getLogger(SendAdminNotificationsViewController.class);

    private static final Map<String, String> BATCH_TOKEN_MAP = new LinkedHashMap<>();
    static {
        BATCH_TOKEN_MAP.put("tuesday_6_7", "Tuesday Batch (6-7 PM)");
        BATCH_TOKEN_MAP.put("tuesday_7_8", "Tuesday Batch (7-8 PM)");
        BATCH_TOKEN_MAP.put("tuesday",     "Tuesday Batch");
        BATCH_TOKEN_MAP.put("monday",      "Monday Batch");
    }

    private static final List<String> BATCH_OPTIONS = List.of(
            "Tuesday Batch (6-7 PM)", "Monday Batch",
            "Tuesday Batch (7-8 PM)", "Tuesday Batch", "Other"
    );

    @FXML private DatePicker datePicker;
    @FXML private TextField classField;
    @FXML private ComboBox<String> batchComboBox;
    @FXML private Label customBatchLabel;
    @FXML private TextField customBatchField;
    @FXML private TextField topicField;
    @FXML private TextField headingField;
    @FXML private TextField totalMarksField;
    @FXML private Label filePathLabel;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private HBox customBatchRow;

    private File excelFile;
    private File topperTemplateFile;
    private File absentTemplateFile;

    private final ExcelReaderService excelReaderService = new ExcelReaderService();
    private final ExcelWriterService excelWriterService = new ExcelWriterService();
    private final ResultImageService resultImageService = new ResultImageService();
    private final WhatsAppApiService whatsAppApiService = new WhatsAppApiService();
    private final GoogleDriveService googleDriveService = new GoogleDriveService();

    private static final String MASTER_SPREADSHEET_ID = "16kElua-wgKkFJRkW8dzOPv9wzadYmKV9P_ydbr3BPFk";

    @FXML
    public void initialize() {
        logger.info("Initializing SendAdminNotificationsViewController");
        try {
            topperTemplateFile = loadTemplateFromResource("topper_template.html", "topper_template");
            absentTemplateFile = loadTemplateFromResource("absent_template.html", "absent_template");
        } catch (IOException e) {
            logger.error("Failed to load templates", e);
            showAlertDirect("Error", "Failed to load templates: " + e.getMessage());
        }

        batchComboBox.setItems(FXCollections.observableArrayList(BATCH_OPTIONS));
        batchComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isOther = "Other".equals(newVal);
            customBatchRow.setVisible(isOther);
            customBatchRow.setManaged(isOther);
        });
    }

    private File loadTemplateFromResource(String resourceName, String prefix) throws IOException {
        InputStream stream = getClass().getResourceAsStream("/" + resourceName);
        if (stream == null) throw new IOException(resourceName + " not found in resources.");
        File tmp = File.createTempFile(prefix, ".html");
        tmp.deleteOnExit();
        Files.copy(stream, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return tmp;
    }

    @FXML
    public void handleChooseFile() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xls", "*.xlsx"));
        excelFile = fc.showOpenDialog(null);
        if (excelFile != null) {
            filePathLabel.setText(excelFile.getName());
            logger.info("Excel file selected: {}", excelFile.getAbsolutePath());
            autoPopulateFromFilename(excelFile.getName());
        }
    }

    private void autoPopulateFromFilename(String filename) {
        String baseName = filename.replaceFirst("\\.[^.]+$", "").toLowerCase();
        Pattern p = Pattern.compile("^file_([^_]+)_(.+)_student_data$", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(baseName);
        if (m.find()) {
            classField.setText(m.group(1).toUpperCase());
            String batchToken = m.group(2).toLowerCase();
            String matched = BATCH_TOKEN_MAP.get(batchToken);
            batchComboBox.setValue(matched != null ? matched : "Other");
        } else {
            classField.setText("");
            batchComboBox.setValue("Other");
        }
    }

    private String getBatchValue() {
        String sel = batchComboBox.getValue();
        return "Other".equals(sel) ? customBatchField.getText().trim() : (sel != null ? sel : "");
    }

    @FXML
    public void handleProceed() {
        logger.info("handleProceed clicked");
        if (!validateInputs()) return;

        final String classNameStr = classField.getText();
        final String batchStr = getBatchValue();

        new Thread(() -> {
            File pngDir = null;
            FileAppender<ILoggingEvent> runLogAppender = null;
            try {
                String uuid = UUID.randomUUID().toString();
                File htmlDir = new File(uuid + "_html");
                pngDir = new File(uuid + "_png");
                htmlDir.mkdirs();
                pngDir.mkdirs();
                runLogAppender = RunLogger.start(pngDir);
                logger.info("Machine GUID: {}, User: {}", MachineIdentifier.getMachineGuid(), MachineIdentifier.getUserName());

                // Copy input Excel to output dir for reference
                Files.copy(excelFile.toPath(), new File(pngDir, excelFile.getName()).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);

                // Cloud sync
                updateProgress("Syncing with Google Cloud...", 0.05);
                File cloudContactsFile = null;
                try {
                    cloudContactsFile = new File(pngDir, "cloud_contacts.xlsx");
                    googleDriveService.downloadSpreadsheetAsExcel(MASTER_SPREADSHEET_ID, cloudContactsFile);
                    logger.info("Cloud sync successful: {}", cloudContactsFile.getAbsolutePath());
                } catch (Exception e) {
                    logger.error("Cloud sync failed", e);
                    cloudContactsFile = null;
                    Platform.runLater(() -> showAlertDirect("Cloud Sync Warning",
                            "Failed to download latest contacts from Google Sheets. Using local data if available.\nError: " + e.getMessage()));
                }

                // Validate
                updateProgress("Validation in progress...", -1);
                ExcelReaderService.ExcelReadResult readResult = excelReaderService.readAndValidate(excelFile, cloudContactsFile, classNameStr, batchStr);

                if (!readResult.success) {
                    updateProgress("Validation failed.", 0);
                    StringBuilder msg = new StringBuilder("Validation failed:\n\n");
                    for (String err : readResult.validationErrors) {
                        msg.append("• ").append(err).append("\n");
                    }
                    if (cloudContactsFile != null) {
                        msg.append("\nTIP: Please check student names in the downloaded contact file:\n");
                        msg.append("File: ").append(cloudContactsFile.getName()).append("\n");
                        if (readResult.targetSheetName != null) {
                            msg.append("Sheet: ").append(readResult.targetSheetName).append("\n");
                        }
                        File finalCloudFile = cloudContactsFile;
                        Platform.runLater(() -> {
                            try { Desktop.getDesktop().open(finalCloudFile); }
                            catch (Exception e) { logger.error("Failed to open cloud contacts file", e); }
                        });
                    }
                    showAlert("Validation Error", msg.toString());
                    return;
                }
                updateProgress("Validation completed.", 0);

                // Dialogs
                List<Student> students = readResult.students;
                boolean[] proceed = {false};
                Double[] cutOff = {null};
                CountDownLatch latch = new CountDownLatch(1);
                Platform.runLater(() -> {
                    try {
                        String absentMsg = readResult.absentStudentNames.isEmpty()
                                ? "No absent summary (all present)" : "Absent list";
                        if (!ConfirmationViewController.show(students, true, false,
                                List.of("Topper list", absentMsg))) return;
                        cutOff[0] = CutOffViewController.show(students);
                        if (cutOff[0] != null) proceed[0] = true;
                    } catch (IOException e) {
                        logger.error("Failed to load dialog", e);
                    } finally {
                        latch.countDown();
                    }
                });
                latch.await();
                if (!proceed[0]) { updateProgress("", 0); return; }

                String formattedDate = datePicker.getValue().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                String whatsAppDate  = datePicker.getValue().format(DateTimeFormatter.ofPattern("dd-MMM"));
                String className   = classNameStr;
                String batch       = batchStr;
                String topic       = topicField.getText();
                String heading     = headingField.getText();
                String totalMarks  = totalMarksField.getText();

                List<String> sendErrors = new ArrayList<>();
                List<MessageDelivery> deliveryRecords = new ArrayList<>();
                int successCount = 0;
                boolean[] quotaExceeded = {false};
                String[] quotaStudentName = {""};

                // Generate topper image
                updateProgress("Generating topper image...", 0.3);
                List<Student> toppers = new ArrayList<>();
                for (Student s : students) {
                    if (s.getMarksObtained() >= cutOff[0]) toppers.add(s);
                }
                toppers.sort((a, b) -> Double.compare(b.getMarksObtained(), a.getMarksObtained()));

                resultImageService.generateTopperImage(
                        toppers, formattedDate, className, batch, topic,
                        totalMarks, topperTemplateFile, htmlDir, pngDir);
                File topperImage = new File(pngDir, "Toppers_List.png");
                String topperMsgId = null;
                // Send topper image to admin
                if (!quotaExceeded[0]) {
                    try {
                        String topperMediaId = whatsAppApiService.uploadMedia(topperImage);
                        topperMsgId = whatsAppApiService.sendTopperResult(topperMediaId, heading, whatsAppDate, className, topic);
                        deliveryRecords.add(new MessageDelivery("ADMIN – Topper List", "Nupur Madam", topperMsgId));
                        successCount++;
                        logger.info("Topper result sent to admin, wamid={}", topperMsgId);
                    } catch (WhatsAppApiService.QuotaExceededException e) {
                        quotaExceeded[0] = true;
                        quotaStudentName[0] = "Topper List";
                        deliveryRecords.add(new MessageDelivery("ADMIN – Topper List", "Nupur Madam", null, "Failed"));
                        sendErrors.add("Quota exceeded — topper image not sent to admin.");
                        logger.error("Quota exceeded sending topper result", e);
                    } catch (Exception e) {
                        sendErrors.add("Topper image (admin): " + e.getMessage());
                        deliveryRecords.add(new MessageDelivery("ADMIN – Topper List", "Nupur Madam", null, "Failed"));
                        logger.error("Failed sending topper result", e);
                    }
                }

                String adminAbsentWamid = null;
                boolean isAbsent = false;
                // Generate and send absent image
                updateProgress("Generating absent image...", 0.7);
                if (!readResult.absentStudentNames.isEmpty()) {
                    isAbsent = true;
                    try {
                        File absentImage = resultImageService.generateAbsentImage(
                                readResult.absentStudentNames, formattedDate, className, batch, topic,
                                absentTemplateFile, htmlDir, pngDir);
                        logger.info("Absent notice image generated: {}", absentImage.getName());

                        if (!quotaExceeded[0]) {
                            try {
                                String absentMediaId = whatsAppApiService.uploadMedia(absentImage);
                                String absentWamid = whatsAppApiService.sendAbsentSummary(absentMediaId, whatsAppDate, className, topic, batch);
                                adminAbsentWamid = absentWamid;
                                deliveryRecords.add(new MessageDelivery("ADMIN – Absent Summary", "Nupur Madam", absentWamid));
                                successCount++;
                                logger.info("Absent summary sent to admin, wamid={}", absentWamid);
                            } catch (WhatsAppApiService.QuotaExceededException e) {
                                quotaExceeded[0] = true;
                                quotaStudentName[0] = "Absent List";
                                deliveryRecords.add(new MessageDelivery("ADMIN – Absent Summary", "Nupur Madam", null, "Failed"));
                                sendErrors.add("Quota exceeded — absent summary not sent to admin.");
                                logger.error("Quota exceeded sending absent summary", e);
                            } catch (Exception e) {
                                sendErrors.add("Absent summary (admin): " + e.getMessage());
                                deliveryRecords.add(new MessageDelivery("ADMIN – Absent Summary", "Nupur Madam", null, "Failed"));
                                logger.error("Failed sending absent summary", e);
                            }
                        }
                    } catch (Exception e) {
                        sendErrors.add("Absent notice image: " + e.getMessage());
                        deliveryRecords.add(new MessageDelivery("ADMIN – Absent Summary", "Nupur Madam", null, "Failed"));
                        logger.error("Failed generating absent notice image", e);
                    }
                } else {
                    if (!quotaExceeded[0]) {
                        try {
                            String noAbsentWamid = whatsAppApiService.sendNoAbsentSummary(whatsAppDate, className, topic, batch);
                            adminAbsentWamid = noAbsentWamid;
                            deliveryRecords.add(new MessageDelivery("ADMIN – No Absent Summary", "Nupur Madam", noAbsentWamid));
                            successCount++;
                            logger.info("No Absent summary sent to admin, wamid={}", noAbsentWamid);
                        } catch (WhatsAppApiService.QuotaExceededException e) {
                            quotaExceeded[0] = true;
                            quotaStudentName[0] = "No Absent";
                            deliveryRecords.add(new MessageDelivery("ADMIN – No Absent Summary", "Nupur Madam", null, "Failed"));
                            sendErrors.add("Quota exceeded — no_absent summary not sent to admin.");
                            logger.error("Quota exceeded sending no_absent summary", e);
                        } catch (Exception e) {
                            sendErrors.add("No Absent summary (admin): " + e.getMessage());
                            deliveryRecords.add(new MessageDelivery("ADMIN – No Absent Summary", "Nupur Madam", null, "Failed"));
                            logger.error("Failed sending no_absent summary", e);
                        }
                    }
                }

                // 5.7 Write run report to PNG directory
                File reportFile = new File(pngDir, "run_report.xlsx");
                try {
                    excelWriterService.writeRunReport(new LinkedList<>(), topperMsgId, null, adminAbsentWamid, isAbsent, false, true, reportFile);
                } catch (Exception e) {
                    logger.error("Failed to write run report", e);
                }

                final int finalSuccess = successCount;
                final List<String> finalErrors = new ArrayList<>(sendErrors);
                final List<MessageDelivery> finalDeliveryRecords = new ArrayList<>(deliveryRecords);
                final boolean finalQuotaExceeded = quotaExceeded[0];
                final String finalQuotaStudentName = quotaStudentName[0];
                Platform.runLater(() -> {
                    try {
                        CompletionSummaryViewController.show(
                                0, 0, false, finalSuccess, 2,
                                finalQuotaExceeded, finalQuotaStudentName, finalErrors,
                                finalDeliveryRecords);
                    } catch (IOException e) {
                        logger.error("Failed to load completion summary dialog", e);
                    }
                });

            } catch (Exception e) {
                logger.error("Unexpected error during processing", e);
                showAlert("Error", "An error occurred: " + e.getMessage());
            } finally {

                if (pngDir != null) {
                    try {
                        googleDriveService.uploadRunFolder(pngDir, LocalDate.now(), MachineIdentifier.getUserName());
                    } catch (Exception e) {
                        logger.error("Failed to upload run folder to Google Drive", e);
                    }
                    File finalPngDir = pngDir;
                    try { Desktop.getDesktop().open(finalPngDir); }
                    catch (Exception e) { logger.warn("Could not open output folder", e); }
                }
                updateProgress("Processing complete.", 1.0);
                if (runLogAppender != null) RunLogger.stop(runLogAppender);
            }
        }).start();
    }

    @FXML
    public void handleBackToHome() {
        try { Main.showMainView(); }
        catch (IOException e) { logger.error("Failed to show main view", e); }
    }

    private boolean validateInputs() {
        if (excelFile == null) {
            showAlertDirect("Error", "Please select an Excel file first."); return false;
        }
        if (datePicker.getValue() == null) {
            showAlertDirect("Error", "Test Conducted Date is required."); return false;
        }
        if (classField.getText().isEmpty()) {
            showAlertDirect("Error", "Class is required."); return false;
        }
        if (batchComboBox.getValue() == null) {
            showAlertDirect("Error", "Batch is required."); return false;
        }
        if ("Other".equals(batchComboBox.getValue()) && customBatchField.getText().trim().isEmpty()) {
            showAlertDirect("Error", "Please enter a custom batch name."); return false;
        }
        if (topicField.getText().isEmpty() || headingField.getText().isEmpty() || totalMarksField.getText().isEmpty()) {
            showAlertDirect("Error", "Topic, Result Heading, and Total Marks are required."); return false;
        }
        try {
            double tm = Double.parseDouble(totalMarksField.getText());
            if (tm <= 0) { showAlertDirect("Error", "Total marks must be positive."); return false; }
        } catch (NumberFormatException e) {
            showAlertDirect("Error", "Total marks must be a valid number."); return false;
        }
        return true;
    }

    private void updateProgress(String message, double progress) {
        Platform.runLater(() -> {
            progressLabel.setText(message);
            if (progress >= 0) progressBar.setProgress(progress);
        });
    }

    private void showAlertDirect(String title, String message) {
        if (title.equals("Info") || title.equals("Warning"))
            MessageDialogViewController.showInfo(title, message);
        else
            MessageDialogViewController.showError(title, message);
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> showAlertDirect(title, message));
    }
}
