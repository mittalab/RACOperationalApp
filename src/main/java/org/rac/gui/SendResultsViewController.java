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
import org.rac.services.ResultImageService;
import org.rac.services.WhatsAppApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
    @FXML private TextField classField;
    @FXML private ComboBox<String> batchComboBox;
    @FXML private Label customBatchLabel;
    @FXML private TextField customBatchField;
    @FXML private TextField topicField;
    @FXML private TextField headingField;
    @FXML private TextField totalMarksField;
    @FXML private Label filePathLabel;
    @FXML private CheckBox sendWhatsAppCheckbox;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private HBox customBatchRow;

    private File excelFile;
    private File templateFile;
    private File topperTemplateFile;
    private boolean filenameMatchedPattern = false;

    private final ExcelReaderService excelReaderService = new ExcelReaderService();
    private final ResultImageService resultImageService = new ResultImageService();
    private final WhatsAppApiService whatsAppApiService = new WhatsAppApiService();
    private final ExcelWriterService excelWriterService = new ExcelWriterService();

    private volatile boolean isAborted = false;
    private final List<Student> sentStudents = Collections.synchronizedList(new ArrayList<>());

    @FXML
    public void initialize() {
        logger.info("Initializing SendResultsViewController");
        try {
            templateFile = loadTemplateFromResource("result_template_4.html", "result_template_4");
            topperTemplateFile = loadTemplateFromResource("topper_template.html", "topper_template");
        } catch (IOException e) {
            logger.error("Failed to load templates", e);
            showAlertDirect("Error", "Failed to load templates: " + e.getMessage());
        }

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
            classField.setText(m.group(1).toUpperCase());
            String batchToken = m.group(2).toLowerCase();
            String matched = BATCH_TOKEN_MAP.get(batchToken);
            batchComboBox.setValue(matched != null ? matched : "Other");
        } else {
            filenameMatchedPattern = false;
            classField.setText("");
            batchComboBox.setValue("Other");
        }
        logger.info("Auto-populated: class='{}', batch='{}', standardFormat={}",
                classField.getText(), batchComboBox.getValue(), filenameMatchedPattern);
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
        new Thread(() -> {
            File pngDir = null;
            try {
                // 1. Validate
                updateProgress("Validation in progress...", -1);
                ExcelReaderService.ExcelReadResult readResult = excelReaderService.readAndValidate(excelFile, filenameMatchedPattern);

                if (!readResult.success) {
                    updateProgress("Validation failed.", 0);
                    StringBuilder msg = new StringBuilder("Validation failed:\n\n");
                    for (String err : readResult.validationErrors) {
                        msg.append("• ").append(err).append("\n");
                    }
                    showAlert("Validation Error", msg.toString());
                    return;
                }
                updateProgress("Validation completed.", 0);

                // 2. Dialogs on FX thread
                List<Student> students = readResult.students;
                boolean[] proceed = {false};
                Double[] cutOff = {null};
                CountDownLatch latch = new CountDownLatch(1);
                Platform.runLater(() -> {
                    try {
                        if (!ConfirmationViewController.show(students.size(), sendWA)) return;
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

                // 3. Temp dirs
                String uuid = UUID.randomUUID().toString();
                File htmlDir = new File(uuid + "_html");
                pngDir = new File(uuid + "_png");
                htmlDir.mkdirs();
                pngDir.mkdirs();

                // Capture form values (read on FX thread, but fields are only written on FX so safe to read here)
                String formattedDate = datePicker.getValue().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                String whatsAppDate  = datePicker.getValue().format(DateTimeFormatter.ofPattern("dd-MMM"));
                String className   = classField.getText();
                String batch       = getBatchValue();
                String topic       = topicField.getText();
                String heading     = headingField.getText();
                String totalMarks  = totalMarksField.getText();

                int total = students.size();
                int successCount = 0;
                List<String> sendErrors = new ArrayList<>();
                List<MessageDelivery> deliveryRecords = new ArrayList<>();
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

                    if (sendWA && !quotaExceeded[0]) {
                        try {
                            String phone = normalisePhone(student.getPhone());
                            String mediaId = whatsAppApiService.uploadMedia(imageFile);
                            String msgId = whatsAppApiService.sendStudentResult(phone, mediaId, student.getName(), whatsAppDate);
                            deliveryRecords.add(new MessageDelivery(student.getName(), phone, msgId));
                            successCount++;
                            logger.info("WhatsApp sent: {} → {}", student.getName(), phone);
                        } catch (WhatsAppApiService.QuotaExceededException e) {
                            quotaExceeded[0] = true;
                            quotaStudentName[0] = student.getName();
                            sendErrors.add("Quota exceeded at student " + student.getName()
                                    + " (" + (i + 1) + "/" + total + ") — WhatsApp sending stopped.");
                            logger.error("WhatsApp quota exceeded at student {}", student.getName(), e);
                        } catch (Exception e) {
                            String errMsg = student.getName() + ": " + e.getMessage();
                            sendErrors.add(errMsg);
                            logger.error("WhatsApp failed for {}", student.getName(), e);
                        }
                    }

                    sentStudents.add(student);
                }

                // 5. Topper image + admin messages
                if (!isAborted) {
                    List<Student> toppers = new ArrayList<>();
                    for (Student s : students) {
                        if (s.getMarksObtained() >= cutOff[0]) toppers.add(s);
                    }
                    toppers.sort((a, b) -> Double.compare(b.getMarksObtained(), a.getMarksObtained()));

                    resultImageService.generateTopperImage(
                            toppers, formattedDate, className, batch, topic,
                            totalMarks, topperTemplateFile, htmlDir, pngDir);
                    File topperImage = new File(pngDir, "Toppers_List.png");

                    if (sendWA && !quotaExceeded[0]) {
                        try {
                            String topperMediaId = whatsAppApiService.uploadMedia(topperImage);
                            whatsAppApiService.sendTopperResult(topperMediaId, heading, whatsAppDate, className, topic);
                            logger.info("Topper result sent to admin");
                        } catch (WhatsAppApiService.QuotaExceededException e) {
                            sendErrors.add("Quota exceeded — topper image not sent to admin.");
                            logger.error("Quota exceeded sending topper result", e);
                        } catch (Exception e) {
                            sendErrors.add("Topper image (admin): " + e.getMessage());
                            logger.error("Failed sending topper result", e);
                        }
                        try {
                            whatsAppApiService.sendResultSummary(heading, whatsAppDate, className, topic, successCount);
                            logger.info("Result summary sent to admin");
                        } catch (WhatsAppApiService.QuotaExceededException e) {
                            sendErrors.add("Quota exceeded — result summary not sent to admin.");
                            logger.error("Quota exceeded sending result summary", e);
                        } catch (Exception e) {
                            sendErrors.add("Result summary (admin): " + e.getMessage());
                            logger.error("Failed sending result summary", e);
                        }
                    }
                }

                // 6. Completion summary
                final int finalSuccess = successCount;
                final int finalTotal = total;
                final List<String> finalErrors = new ArrayList<>(sendErrors);
                final List<MessageDelivery> finalDeliveryRecords = new ArrayList<>(deliveryRecords);
                final boolean waEnabled = sendWA;
                final boolean finalQuotaExceeded = quotaExceeded[0];
                final String finalQuotaStudentName = quotaStudentName[0];
                Platform.runLater(() -> {
                    try {
                        CompletionSummaryViewController.show(
                                finalSuccess, finalTotal, waEnabled,
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
                    File finalPngDir = pngDir;
                    try { Desktop.getDesktop().open(finalPngDir); }
                    catch (Exception e) { logger.warn("Could not open output folder", e); }
                }
                updateProgress(isAborted ? "Aborted." : "Processing complete.", isAborted ? 0 : 1.0);
                if (isAborted) Platform.runLater(this::handleAbort);
            }
        }).start();
    }

    private String normalisePhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        return digits.length() == 10 ? "91" + digits : digits;
    }

    @FXML
    public void handleAbort() {
        logger.info("Abort triggered");
        isAborted = true;
        if (!sentStudents.isEmpty()) {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
            fc.setInitialFileName("aborted_session_report.xlsx");
            File file = fc.showSaveDialog(null);
            if (file != null) {
                try {
                    excelWriterService.writeStudentsToExcel(sentStudents, file);
                    showAlertDirect("Info", "Abort report saved: " + file.getAbsolutePath());
                } catch (IOException e) {
                    logger.error("Error saving abort report", e);
                    showAlertDirect("Error", "Error saving abort report: " + e.getMessage());
                }
            }
        }
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
        Alert.AlertType type = (title.equals("Info") || title.equals("Warning"))
                ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR;
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> showAlertDirect(title, message));
    }
}
