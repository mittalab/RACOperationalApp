package org.rac.gui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import org.rac.Main;
import org.rac.model.Student;
import org.rac.services.EmailService;
import org.rac.services.ExcelReaderService;
import org.rac.services.ExcelWriterService;
import org.rac.services.ResultImageService;
import org.rac.services.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;


public class SendResultsViewController {

    private static final Logger logger = LoggerFactory.getLogger(SendResultsViewController.class);

    @FXML
    private DatePicker datePicker;
    @FXML
    private TextField classField;
    @FXML
    private TextField batchField;
    @FXML
    private TextField topicField;
    @FXML
    private TextField headingField;
    @FXML
    private TextField totalMarksField;
    @FXML
    private Label filePathLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label progressLabel;

    private File excelFile;
    private File templateFile;
    private File topperTemplateFile;
    private final ExcelReaderService excelReaderService = new ExcelReaderService();
    private final ResultImageService resultImageService = new ResultImageService();
    private final WhatsAppService whatsAppService = new WhatsAppService();
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
            showAlert("Error", "Failed to load templates: " + e.getMessage());
        }

        datePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                if (newValue.getDayOfWeek() == java.time.DayOfWeek.SATURDAY) {
                    batchField.setText("Monday Batch");
                } else {
                    batchField.setText("Tuesday Batch");
                }
            }
        });
    }

    private File loadTemplateFromResource(String resourceName, String prefix) throws IOException {
        InputStream resourceStream = getClass().getResourceAsStream("/" + resourceName);
        if (resourceStream == null) {
            throw new IOException(resourceName + " not found in resources.");
        }
        File tempFile = File.createTempFile(prefix, ".html");
        tempFile.deleteOnExit();
        Files.copy(resourceStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        logger.info("Loaded {} to temporary file: {}", resourceName, tempFile.getAbsolutePath());
        return tempFile;
    }


    @FXML
    public void handleChooseFile() {
        logger.info("handleChooseFile button clicked");
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xls", "*.xlsx"));
        excelFile = fileChooser.showOpenDialog(null);
        if (excelFile != null) {
            filePathLabel.setText(excelFile.getName());
            logger.info("Excel file selected: {}", excelFile.getAbsolutePath());
        } else {
            logger.warn("No Excel file selected");
        }
    }
    

    @FXML
    public void handleProceed() {
        logger.info("handleProceed button clicked");
        // 1. Validate inputs
        if (!validateInputs()) {
            logger.warn("Input validation failed");
            return;
        }
        logger.info("Input validation successful");

        // 2. Read excel file
        List<Student> students;
        try {
            logger.info("Reading students from Excel file: {}", excelFile.getAbsolutePath());
            students = excelReaderService.readStudentsFromExcel(excelFile);
            logger.info("Found {} students in the Excel file", students.size());
        } catch (IOException e) {
            logger.error("Error reading Excel file", e);
            showAlert("Error", "Error reading Excel file: " + e.getMessage());
            return;
        }

        // 3. Show summary and get confirmation
        if (!showSummaryAndGetConfirmation(students.size())) {
            logger.info("User cancelled the operation from the summary dialog");
            return;
        }

        // 3.b Show marks profile and get cut-off
        Double cutOff = showMarksProfileAndGetCutOff(students);
        if (cutOff == null) {
            logger.info("User cancelled from the marks profile dialog");
            return;
        }

        // 4.a creating the tmp folders
        // 1. Generate UUID
        String uuid = UUID.randomUUID().toString();
        File htmlDir = new File(uuid + "_html");
        File pngDir = new File(uuid + "_png");

        htmlDir.mkdirs();
        pngDir.mkdirs();

        // 4. Start processing
        isAborted = false;
        sentStudents.clear();
        final List<Student> finalStudents = students;
        new Thread(() -> {
            logger.info("Starting the result sending process in a new thread");
            String outputDestination = "todo";
            try {
                if ("WhatsApp".equals(outputDestination)) {
                    whatsAppService.startService();
                }

                int totalStudents = finalStudents.size();
                for (int i = 0; i < totalStudents; i++) {
                    if (isAborted) {
                        logger.warn("Process aborted by user");
                        break;
                    }
                    Student student = finalStudents.get(i);
                    logger.info("Processing student {} of {}: {}", (i + 1), totalStudents, student.getName());
                    final int currentStudentIndex = i;
                    Platform.runLater(() -> {
                        progressLabel.setText("Processing " + (currentStudentIndex + 1) + " of " + totalStudents + ": " + student.getName());
                        progressBar.setProgress((double) (currentStudentIndex + 1) / totalStudents);
                    });

                    // Generate image
                    logger.debug("Generating result image for student: {}", student.getName());
                    File imageFile = resultImageService.generateImage(
                            student,
                            datePicker.getValue().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")),
                            classField.getText(),
                            topicField.getText(),
                            headingField.getText(),
                            totalMarksField.getText(),
                            templateFile,
                            currentStudentIndex,
                            htmlDir,
                            pngDir
                    );
                    //logger.debug("Result image generated: {}", imageFile.getAbsolutePath());

                    // Send message based on destination
//                    if ("WhatsApp".equals(outputDestination)) {
//                        logger.info("Successfully sent WhatsApp message to {}", student.getName());
//                    } else if ("Email".equals(outputDestination)) {
//                        String recipient = ""; // Assuming student has email or using override
//                        if (recipient == null || recipient.isEmpty()) {
//                            logger.warn("Skipping email for {} as no recipient email found.", student.getName());
//                            Platform.runLater(() -> showAlert("Warning", "Skipping email for " + student.getName() + " as no recipient email found."));
//                            continue;
//                        }
//                        String subject = "RAC Result";
//                        String sender = "29.abhishek.mittal@gmail.com";
//                        logger.debug("Sending email to {} from {} with subject '{}'", recipient, sender, subject);
//                        emailService.sendEmailWithAttachment(recipient, sender, subject, "Please find your result attached.", imageFile);
//                        logger.info("Successfully sent email to {}", student.getName());
//                    }
                    sentStudents.add(student);
                }

                if (!isAborted) {
                    // Generate Topper List Image
                    logger.info("Generating Topper List Image");
                    List<Student> toppers = new ArrayList<>();
                    for (Student s : finalStudents) {
                        if (s.getMarksObtained() >= cutOff) {
                            toppers.add(s);
                        }
                    }
                    toppers.sort((s1, s2) -> Double.compare(s2.getMarksObtained(), s1.getMarksObtained()));

                    resultImageService.generateTopperImage(
                            toppers,
                            datePicker.getValue().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")),
                            classField.getText(),
                            batchField.getText(),
                            topicField.getText(),
                            totalMarksField.getText(),
                            topperTemplateFile,
                            htmlDir,
                            pngDir
                    );
                }
            } catch (Exception e) {
                logger.error("An error occurred during the result sending process", e);
                showAlert("Error", "An error occurred during processing: " + e.getMessage());
            } finally {
                // 9. Open PNG folder automatically
                try {
                    Desktop.getDesktop().open(pngDir);
                } catch (Exception e) {
                    System.out.println("Could not open folder automatically.");
                }

                if ("WhatsApp".equals(outputDestination)) {
                    whatsAppService.stopService();
                }
                Platform.runLater(() -> {
                    if(isAborted){
                        handleAbort();
                    }
                    progressLabel.setText("Processing complete.");
                    logger.info("Result sending process finished");
                });
            }
        }).start();


    }

    private Double showMarksProfileAndGetCutOff(List<Student> students) {
        int totalStudents = students.size();
        Map<Double, Integer> distribution = new TreeMap<>(Collections.reverseOrder());
        for (Student s : students) {
            distribution.put(s.getMarksObtained(), distribution.getOrDefault(s.getMarksObtained(), 0) + 1);
        }

        StringBuilder profileText = new StringBuilder("Total Students: " + totalStudents + "\n\nMarks Distribution:\n");
        for (Map.Entry<Double, Integer> entry : distribution.entrySet()) {
            profileText.append(String.format("%.1f Marks: %d Students\n", entry.getKey(), entry.getValue()));
        }

        TextInputDialog dialog = new TextInputDialog("");
        dialog.setTitle("Set Cut-off for Toppers");
        dialog.setHeaderText(profileText.toString());
        dialog.setContentText("Enter Cut-off Marks:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            try {
                return Double.parseDouble(result.get());
            } catch (NumberFormatException e) {
                showAlert("Error", "Invalid cut-off marks entered.");
                return null;
            }
        }
        return null;
    }
    
    private boolean showSummaryAndGetConfirmation(int studentCount) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation");
        alert.setHeaderText("Summary");
        alert.setContentText("Sending the result for " + studentCount + " students.");

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }


    @FXML
    public void handleAbort() {
        logger.info("handleAbort button clicked");
        isAborted = true;
        if (!sentStudents.isEmpty()) {
            logger.info("Generating report for {} students who received the message before abortion", sentStudents.size());
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
            fileChooser.setInitialFileName("aborted_session_report.xlsx");
            File file = fileChooser.showSaveDialog(null);
            if (file != null) {
                try {
                    excelWriterService.writeStudentsToExcel(sentStudents, file);
                    showAlert("Info", "Aborted session report saved to: " + file.getAbsolutePath());
                } catch (IOException e) {
                    logger.error("Error saving abort report", e);
                    showAlert("Error", "Error saving abort report: " + e.getMessage());
                }
            } else {
                logger.warn("User did not select a file to save the abort report");
            }
        } else {
            logger.info("Abort was called, but no messages were sent");
        }
    }

    @FXML
    public void handleBackToHome() {
        logger.info("handleBackToHome button clicked");
        try {
            Main.showMainView();
        } catch (IOException e) {
            logger.error("Failed to show main view", e);
        }
    }

    private boolean validateInputs() {
        // Common validations
        if (datePicker.getValue() == null || classField.getText().isEmpty() || topicField.getText().isEmpty() ||
                headingField.getText().isEmpty() || totalMarksField.getText().isEmpty() || excelFile == null) {
            showAlert("Error", "All common fields (Date, Class, Topic, Heading, Total Marks, Excel File) are required.");
            return false;
        }
        try {
            double totalMarks = Double.parseDouble(totalMarksField.getText());
            if (totalMarks <= 0) {
                showAlert("Error", "Total marks must be a positive number.");
                return false;
            }
        } catch (NumberFormatException e) {
            showAlert("Error", "Total marks must be a valid number.");
            return false;
        }

        return true;
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            if(title.equals("Info") || title.equals("Warning")){
                alert.setAlertType(Alert.AlertType.INFORMATION); // Use INFORMATION for Info/Warning
            }
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
