package org.rac.gui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import org.rac.Main;
import org.rac.model.MessageDelivery;
import org.rac.model.RunRecord;
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

public class SendResultsViewController {

    private static final Logger logger = LoggerFactory.getLogger(SendResultsViewController.class);

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
    @FXML private ComboBox<String> classField;
    @FXML private ComboBox<String> batchComboBox;
    @FXML private Label customBatchLabel;
    @FXML private TextField customBatchField;
    @FXML private TextField topicField;
    @FXML private TextField headingField;
    @FXML private TextField totalMarksField;
    @FXML private Label filePathLabel;
    @FXML private CheckBox sendWhatsAppCheckbox;
    @FXML private CheckBox sendAdminNotificationsCheckbox;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private HBox customBatchRow;

    private File excelFile;
    private File templateFile;
    private File topperTemplateFile;
    private File absentTemplateFile;
    private boolean filenameMatchedPattern = false;

    private final ExcelReaderService excelReaderService = new ExcelReaderService();
    private final ResultImageService resultImageService = new ResultImageService();
    private final WhatsAppApiService whatsAppApiService = new WhatsAppApiService();
    private final ExcelWriterService excelWriterService = new ExcelWriterService();
    private final GoogleDriveService googleDriveService = new GoogleDriveService();

    private static final String MASTER_SPREADSHEET_ID = "16kElua-wgKkFJRkW8dzOPv9wzadYmKV9P_ydbr3BPFk";
    //private static final String MASTER_SPREADSHEET_ID = "1WzK6Le_v9jPXcKous50Pjs3smnhC3tWIoDiyhmUXulI";

    private volatile boolean isAborted = false;
    private final List<Student> sentStudents = Collections.synchronizedList(new ArrayList<>());

    private File syncWithCloud(File runDir) throws Exception {
        logger.info("Starting cloud sync with spreadsheet ID: {}", MASTER_SPREADSHEET_ID);
        File cloudFile = new File(runDir, "cloud_contacts.xlsx");
        googleDriveService.downloadSpreadsheetAsExcel(MASTER_SPREADSHEET_ID, cloudFile);
        return cloudFile;
    }

    @FXML
    public void initialize() {
        logger.info("Initializing SendResultsViewController");
        try {
            templateFile = loadTemplateFromResource("result_template_4.html", "result_template_4");
            topperTemplateFile = loadTemplateFromResource("topper_template.html", "topper_template");
            absentTemplateFile = loadTemplateFromResource("absent_template.html", "absent_template");
        } catch (IOException e) {
            logger.error("Failed to load templates", e);
            showAlertDirect("Error", "Failed to load templates: " + e.getMessage());
        }

        classField.setItems(FXCollections.observableArrayList("IX", "X", "XI", "XII"));
        classField.setValue(null);
        batchComboBox.setItems(FXCollections.observableArrayList(BATCH_OPTIONS));

//        batchComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
//            boolean isOther = "Other".equals(newVal);
//            customBatchLabel.setVisible(isOther);
//            customBatchLabel.setManaged(isOther);
//            customBatchField.setVisible(isOther);
//            customBatchField.setManaged(isOther);
//        });
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
            filenameMatchedPattern = true;
            classField.setValue(m.group(1).toUpperCase());
            String batchToken = m.group(2).toLowerCase();
            String matched = BATCH_TOKEN_MAP.get(batchToken);
            batchComboBox.setValue(matched != null ? matched : "Other");
        } else {
            filenameMatchedPattern = false;
            classField.setValue(null);
            batchComboBox.setValue("Other");
        }
        logger.info("Auto-populated: class='{}', batch='{}', standardFormat={}",
                classField.getValue(), batchComboBox.getValue(), filenameMatchedPattern);
    }

    private String getBatchValue() {
        String sel = batchComboBox.getValue();
        return "Other".equals(sel) ? customBatchField.getText().trim() : (sel != null ? sel : "");
    }

    @FXML
    public void handleProceed() {
        logger.info("handleProceed clicked");
        if (!validateInputs()) return;

        isAborted = false;
        sentStudents.clear();

        // Capture UI state on FX thread before background thread starts
        final boolean sendWA = sendWhatsAppCheckbox.isSelected();
        final boolean sendAdminNotifications = sendAdminNotificationsCheckbox.isSelected();
        final String classNameStr = classField.getValue() != null ? classField.getValue() : "";
        final String batchStr = getBatchValue();

        new Thread(() -> {
            File pngDir = null;
            FileAppender<ILoggingEvent> runLogAppender = null;
            try {
                // 1. Setup Dirs early for Cloud Sync
                String uuid = UUID.randomUUID().toString();
                File htmlDir = new File(uuid + "_html");
                pngDir = new File(uuid + "_png");
                htmlDir.mkdirs();
                pngDir.mkdirs();
                runLogAppender = RunLogger.start(pngDir);
                logger.info("Checkbox state — sendWhatsApp: {}, sendAdminNotifications: {}", sendWA, sendAdminNotifications);
                logger.info("Machine GUID: {}, User: {}", MachineIdentifier.getMachineGuid(), MachineIdentifier.getUserName());

                // Copy input Excel to output dir for reference
                Files.copy(excelFile.toPath(), new File(pngDir, excelFile.getName()).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);

                // 2. Cloud Sync
                updateProgress("Syncing with Google Cloud...", 0.05);
                File cloudContactsFile = null;
                try {
                    cloudContactsFile = syncWithCloud(pngDir);
                    logger.info("Cloud sync successful: {}", cloudContactsFile.getAbsolutePath());
                } catch (Exception e) {
                    logger.error("Cloud sync failed", e);
                    Platform.runLater(() -> showAlertDirect("Cloud Sync Warning", 
                        "Failed to download latest contacts from Google Sheets. Using local data if available.\nError: " + e.getMessage()));
                }

                // 3. Validate
                updateProgress("Validation in progress...", -1);
                ExcelReaderService.ExcelReadResult readResult = excelReaderService.readAndValidate(excelFile, cloudContactsFile, classNameStr, batchStr);

                if (!readResult.success) {
                    updateProgress("Validation failed.", 0);
                    
                    if (readResult.mismatchedNames != null && !readResult.mismatchedNames.isEmpty()) {
                        Platform.runLater(() -> {
                            try {
                                MismatchSummaryViewController.show(
                                    readResult.mismatchedNames,
                                    readResult.allContacts,
                                    excelFile,
                                    readResult.targetSheetName
                                );
                            } catch (Exception e) {
                                logger.error("Failed to show mismatch summary dialog", e);
                                showAlert("Validation Error", "Validation failed due to name mismatches: " + readResult.mismatchedNames);
                            }
                        });
                    } else {
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
                    }
                    return;
                }
                updateProgress("Validation completed.", 0);

                // 2. Dialogs on FX thread
                List<Student> students = readResult.students;
                boolean[] proceed = {false};
                Double[] cutOff = {null};
                CountDownLatch latch = new CountDownLatch(1);
                List<String> adminMsgs = new ArrayList<>();
                if (sendWA) {
                    adminMsgs.add("Result summary");
                    if (sendAdminNotifications) {
                        adminMsgs.add("Topper list");
                        if (filenameMatchedPattern) {
                            adminMsgs.add(readResult.absentStudentNames.isEmpty()
                                    ? "No absent summary (all present)"
                                    : "Absent list");
                        }
                    }
                }
                final List<String> finalAdminMsgs = adminMsgs;

                Platform.runLater(() -> {
                    try {
                        if (!ConfirmationViewController.show(students, sendWA, true, finalAdminMsgs)) return;
                        if (sendAdminNotifications) {
                            cutOff[0] = CutOffViewController.show(students);
                            if (cutOff[0] != null) proceed[0] = true;
                        } else {
                            proceed[0] = true;
                        }
                    } catch (IOException e) {
                        logger.error("Failed to load dialog", e);
                    } finally {
                        latch.countDown();
                    }
                });
                latch.await();
                if (!proceed[0]) { updateProgress("", 0); return; }

                // Capture form values (read on FX thread, but fields are only written on FX so safe to read here)
                String formattedDate = datePicker.getValue().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                String whatsAppDate  = datePicker.getValue().format(DateTimeFormatter.ofPattern("dd-MMM"));
                String className   = classNameStr;
                String batch       = batchStr;
                String topic       = topicField.getText();
                String heading     = headingField.getText();
                String totalMarks  = totalMarksField.getText();

                int total = students.size();
                int successCount = 0;
                int adminSuccessCount = 0;
                int adminTotal = 0;
                List<String> sendErrors = new ArrayList<>();
                List<MessageDelivery> deliveryRecords = new ArrayList<>();
                List<RunRecord> runRecords = new ArrayList<>();
                boolean[] quotaExceeded = {false};
                String[] quotaStudentName = {""};

                // 4. Per-student loop
                for (int i = 0; i < total; i++) {
                    if (isAborted) { logger.warn("Aborted at student {}", i + 1); break; }

                    Student student = students.get(i);
                    updateProgress("Processing " + (i + 1) + " of " + total + ": " + student.getName(),
                            (double) (i + 1) / total);

                    File imageFile = resultImageService.generateImage(
                            student, formattedDate, className, topic, heading,
                            totalMarks, templateFile, i, htmlDir, pngDir);

                    List<String> phones = parsePhones(student.getPhone());
                    String msgId = null;
                    boolean anySuccess = false;

                    if (sendWA && !quotaExceeded[0]) {
                        try {
                            String mediaId = whatsAppApiService.uploadMedia(imageFile);
                            for (String phone : phones) {
                                if (quotaExceeded[0]) break;
                                try {
                                    msgId = whatsAppApiService.sendStudentResult(phone, mediaId, student.getName(), whatsAppDate);
                                    deliveryRecords.add(new MessageDelivery(student.getName(), phone, msgId));
                                    anySuccess = true;
                                    logger.info("WhatsApp sent: {} → {}", student.getName(), phone);
                                } catch (WhatsAppApiService.QuotaExceededException e) {
                                    quotaExceeded[0] = true;
                                    quotaStudentName[0] = student.getName();
                                    deliveryRecords.add(new MessageDelivery(student.getName(), phone, null, "Failed"));
                                    sendErrors.add("Quota exceeded at student " + student.getName()
                                            + " (" + (i + 1) + "/" + total + ") — WhatsApp sending stopped.");
                                    logger.error("WhatsApp quota exceeded at student {}", student.getName(), e);
                                } catch (Exception e) {
                                    String errMsg = student.getName() + " (" + phone + "): " + e.getMessage();
                                    sendErrors.add(errMsg);
                                    deliveryRecords.add(new MessageDelivery(student.getName(), phone, null, "Failed"));
                                    logger.error("WhatsApp failed for {} at {}", student.getName(), phone, e);
                                }
                            }
                            if (anySuccess) successCount++;
                        } catch (WhatsAppApiService.QuotaExceededException e) {
                            quotaExceeded[0] = true;
                            quotaStudentName[0] = student.getName();
                            deliveryRecords.add(new MessageDelivery(student.getName(), null, null, "Failed"));
                            sendErrors.add("Quota exceeded at student " + student.getName()
                                    + " (" + (i + 1) + "/" + total + ") — WhatsApp sending stopped.");
                            logger.error("WhatsApp quota exceeded at student {}", student.getName(), e);
                        } catch (Exception e) {
                            String errMsg = student.getName() + ": " + e.getMessage();
                            deliveryRecords.add(new MessageDelivery(student.getName(), null, null, "Failed"));
                            sendErrors.add(errMsg);
                            logger.error("WhatsApp failed for {}", student.getName(), e);
                        }
                    }

//                    if (sendWA && !quotaExceeded[0] && !anySuccess) {
//                        msgId = "FAILED";
//                        MessageDelivery failedDelivery = new MessageDelivery(student.getName(), String.join(", ", phones), "");
//                        failedDelivery.setStatus("Failed ✗");
//                        deliveryRecords.add(failedDelivery);
//                    }
                    String joinedPhones = String.join(", ", phones);
                    runRecords.add(new RunRecord(student.getName(), joinedPhones, imageFile.getName(), msgId));
                    sentStudents.add(student);
                }

                // 5. Topper image + admin messages
                String[] topperMsgId = {null};
                String[] summaryMsgId = {null};
                if (!isAborted && sendAdminNotifications) {
                    List<Student> toppers = new ArrayList<>();
                    for (Student s : students) {
                        if (s.getMarksObtained() >= cutOff[0]) toppers.add(s);
                    }
                    toppers.sort((a, b) -> Double.compare(b.getMarksObtained(), a.getMarksObtained()));

                    updateProgress("Admin: Generating topper image...", -1);
                    resultImageService.generateTopperImage(
                            toppers, formattedDate, className, batch, topic,
                            totalMarks, topperTemplateFile, htmlDir, pngDir);
                    File topperImage = new File(pngDir, "Toppers_List.png");
                    adminTotal++;
                    if (sendWA && !quotaExceeded[0]) {
                        updateProgress("Admin: Sending topper list...", -1);
                        try {
                            String topperMediaId = whatsAppApiService.uploadMedia(topperImage);
                            topperMsgId[0] = whatsAppApiService.sendTopperResult(topperMediaId, heading, whatsAppDate, className, topic);
                            deliveryRecords.add(new MessageDelivery("ADMIN – Topper List", "Nupur Madam", topperMsgId[0]));
                            adminSuccessCount++;
                            logger.info("Topper result sent to admin, wamid={}", topperMsgId[0]);
                        } catch (WhatsAppApiService.QuotaExceededException e) {
                            sendErrors.add("Quota exceeded — topper image not sent to admin.");
                            deliveryRecords.add(new MessageDelivery("ADMIN – Topper List", "Nupur Madam", null, "Failed"));
                            logger.error("Quota exceeded sending topper result", e);
                        } catch (Exception e) {
                            sendErrors.add("Topper image (admin): " + e.getMessage());
                            deliveryRecords.add(new MessageDelivery("ADMIN – Topper List", "Nupur Madam", null, "Failed"));
                            logger.error("Failed sending topper result", e);
                        }
                    }
                }

                // 5.1 Result summary — always sent to admin when WA is enabled
                if (!isAborted && sendWA && !quotaExceeded[0]) {
                    updateProgress("Admin: Sending result summary...", -1);
                    adminTotal++;
                    try {
                        summaryMsgId[0] = whatsAppApiService.sendResultSummary(heading, whatsAppDate, className, topic, successCount);
                        deliveryRecords.add(new MessageDelivery("ADMIN – Summary", "Nupur Madam", summaryMsgId[0]));
                        adminSuccessCount++;
                        logger.info("Result summary sent to admin, wamid={}", summaryMsgId[0]);
                    } catch (WhatsAppApiService.QuotaExceededException e) {
                        sendErrors.add("Quota exceeded — result summary not sent to admin.");
                        deliveryRecords.add(new MessageDelivery("ADMIN – Summary", "Nupur Madam", null, "Failed"));
                        logger.error("Quota exceeded sending result summary", e);
                    } catch (Exception e) {
                        sendErrors.add("Result summary (admin): " + e.getMessage());
                        deliveryRecords.add(new MessageDelivery("ADMIN – Summary", "Nupur Madam", null, "Failed"));
                        logger.error("Failed sending result summary", e);
                    }
                }

                boolean isAbsent = false;
                String adminAbsentWamid = null;
                // 5.5 Absent notice image + template message
                if (!isAborted && sendAdminNotifications && filenameMatchedPattern) {
                    adminTotal++;
                    if (!readResult.absentStudentNames.isEmpty()) {
                        isAbsent = true;
                        updateProgress("Admin: Generating absent image...", -1);
                        try {
                            File absentImage = resultImageService.generateAbsentImage(
                                    readResult.absentStudentNames, formattedDate, className, batch, topic,
                                    absentTemplateFile, htmlDir, pngDir);
                            logger.info("Absent notice image generated: {}", absentImage.getName());

                            if (sendWA && !quotaExceeded[0]) {
                                updateProgress("Admin: Sending absent list...", -1);
                                try {
                                    String absentMediaId = whatsAppApiService.uploadMedia(absentImage);
                                    String absentWamid = whatsAppApiService.sendAbsentSummary(absentMediaId, whatsAppDate, className, topic, batch);
                                    adminAbsentWamid = absentWamid;
                                    deliveryRecords.add(new MessageDelivery("ADMIN – Absent Summary", "Nupur Madam", absentWamid));
                                    adminSuccessCount++;
                                    logger.info("Absent summary template sent to admin, wamid={}", absentWamid);
                                } catch (WhatsAppApiService.QuotaExceededException e) {
                                    sendErrors.add("Quota exceeded — absent summary not sent to admin.");
                                    deliveryRecords.add(new MessageDelivery("ADMIN – Absent Summary", "Nupur Madam", null, "Failed"));
                                    logger.error("Quota exceeded sending absent summary", e);
                                } catch (Exception e) {
                                    sendErrors.add("Absent summary (admin): " + e.getMessage() + " — image still generated.");
                                    deliveryRecords.add(new MessageDelivery("ADMIN – Absent Summary", "Nupur Madam", null, "Failed"));
                                    logger.error("Failed sending absent summary template (template may not be registered)", e);
                                }
                            }
                        } catch (Exception e) {
                            sendErrors.add("Absent notice image: " + e.getMessage());
                            deliveryRecords.add(new MessageDelivery("ADMIN – Absent Summary", "Nupur Madam", null, "Failed"));
                            logger.error("Failed generating absent notice image", e);
                        }
                    } else {
                        // NO absentees - send no_absent template
                        if (sendWA && !quotaExceeded[0]) {
                            updateProgress("Admin: Sending no-absent summary...", -1);
                            try {
                                String noAbsentWamid = whatsAppApiService.sendNoAbsentSummary(whatsAppDate, className, topic, batch);
                                deliveryRecords.add(new MessageDelivery("ADMIN – No Absent Summary", "Nupur Madam", noAbsentWamid));
                                adminAbsentWamid = noAbsentWamid;
                                adminSuccessCount++;
                                logger.info("No Absent summary template sent to admin, wamid={}", noAbsentWamid);
                            } catch (WhatsAppApiService.QuotaExceededException e) {
                                sendErrors.add("Quota exceeded — no_absent summary not sent to admin.");
                                deliveryRecords.add(new MessageDelivery("ADMIN – No Absent Summary", "Nupur Madam", null, "Failed"));
                                logger.error("Quota exceeded sending no_absent summary", e);
                            } catch (Exception e) {
                                sendErrors.add("No Absent summary (admin): " + e.getMessage());
                                deliveryRecords.add(new MessageDelivery("ADMIN – No Absent Summary", "Nupur Madam", null, "Failed"));
                                logger.error("Failed sending no_absent summary template", e);
                            }
                        }
                    }
                }

                // 5.7 Write run report to PNG directory
                File reportFile = new File(pngDir, "run_report.xlsx");
                try {
                    excelWriterService.writeRunReport(runRecords, topperMsgId[0], summaryMsgId[0], adminAbsentWamid, isAbsent, true, sendAdminNotifications, reportFile);
                } catch (Exception e) {
                    logger.error("Failed to write run report", e);
                }

                // 6. Completion summary
                final int finalSuccess = successCount;
                final int finalTotal = total;
                final int finalAdminSuccess = adminSuccessCount;
                final int finalAdminTodal = adminTotal;
                final List<String> finalErrors = new ArrayList<>(sendErrors);
                final List<MessageDelivery> finalDeliveryRecords = new ArrayList<>(deliveryRecords);
                final boolean waEnabled = sendWA;
                final boolean finalQuotaExceeded = quotaExceeded[0];
                final String finalQuotaStudentName = quotaStudentName[0];
                Platform.runLater(() -> {
                    try {
                        CompletionSummaryViewController.show(
                                finalSuccess, finalTotal, waEnabled, finalAdminSuccess, finalAdminTodal,
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
                if (isAborted && !sentStudents.isEmpty() && pngDir != null) {
                    File reportFile = new File(pngDir, "aborted_session_report.xlsx");
                    try {
                        excelWriterService.writeStudentsToExcel(new ArrayList<>(sentStudents), reportFile);
                    } catch (IOException ex) {
                        logger.error("Error saving abort report", ex);
                        showAlert("Error", "Error saving abort report: " + ex.getMessage());
                    }
                }
                if (pngDir != null) {
                    try {
                        googleDriveService.uploadRunFolder(pngDir, LocalDate.now(), MachineIdentifier.getUserName());
                    } catch (Exception e) {
                        logger.error("Failed to upload run folder to Google Drive", e);
                        showAlert("Warning: Google Drive Upload Failed", "Please inform Abhishek that the Google Drive uploads are failing.\n\nError details: " + e.getMessage());
                    }
                    File finalPngDir = pngDir;
                    try { Desktop.getDesktop().open(finalPngDir); }
                    catch (Exception e) { logger.warn("Could not open output folder", e); }
                }
                updateProgress(isAborted ? "Aborted." : "Processing complete.", isAborted ? 0 : 1.0);
                if (runLogAppender != null) RunLogger.stop(runLogAppender);
            }
        }).start();
    }

    private String normalisePhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        return digits.length() == 10 ? "91" + digits : digits;
    }

    private List<String> parsePhones(String phone) {
        List<String> result = new ArrayList<>();
        if (phone == null || phone.isEmpty()) return result;
        for (String p : phone.split(",")) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) result.add(normalisePhone(trimmed));
        }
        return result;
    }

    @FXML
    public void handleAbort() {
        logger.info("Abort triggered");
        isAborted = true;
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
        if (classField.getValue() == null || classField.getValue().isEmpty()) {
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
        if (title.equals("Info")) {
            MessageDialogViewController.showInfo(title, message);
        } else if (title.equals("Warning") || title.contains("Warning")) {
            MessageDialogViewController.showWarning(title, message);
        } else {
            MessageDialogViewController.showError(title, message);
        }
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> showAlertDirect(title, message));
    }
}
