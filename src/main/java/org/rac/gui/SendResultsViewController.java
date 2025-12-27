package org.rac.gui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class SendResultsViewController {

    private static final Logger logger = LoggerFactory.getLogger(SendResultsViewController.class);

    @FXML
    private DatePicker datePicker;
    @FXML
    private TextField classField;
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

//    @FXML
//    private ChoiceBox<String> outputDestinationChoiceBox;
//    @FXML
//    private TextField senderEmailField;
//    @FXML
//    private TextField recipientEmailField;
//    @FXML
//    private TextField emailSubjectField;

    private File excelFile;
    private File templateFile;
    private final ExcelReaderService excelReaderService = new ExcelReaderService();
    private final ResultImageService resultImageService = new ResultImageService();
    private final WhatsAppService whatsAppService = new WhatsAppService();
    private final EmailService emailService = new EmailService(); // New instance for email
    private final ExcelWriterService excelWriterService = new ExcelWriterService();

    private volatile boolean isAborted = false;
    private final List<Student> sentStudents = Collections.synchronizedList(new ArrayList<>());

    @FXML
    public void initialize() {
        logger.info("Initializing SendResultsViewController");
        try {
            InputStream resourceStream = getClass().getResourceAsStream("/result_template.html");
            if (resourceStream == null) {
                logger.error("result_template.html not found in resources.");
                showAlert("Error", "result_template.html not found in resources.");
                return;
            }
            templateFile = File.createTempFile("result_template", ".html");
            templateFile.deleteOnExit();
            Files.copy(resourceStream, templateFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Loaded result_template.html to temporary file: {}", templateFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to load result_template.html", e);
            showAlert("Error", "Failed to load result_template.html: " + e.getMessage());
        }

//        // Initialize ChoiceBox for output destination
//        outputDestinationChoiceBox.setItems(FXCollections.observableArrayList("WhatsApp", "Email"));
//        outputDestinationChoiceBox.setValue("WhatsApp"); // Default selection
//
//        // Set visibility of email fields based on selection
//        senderEmailField.managedProperty().bind(outputDestinationChoiceBox.valueProperty().isEqualTo("Email"));
//        senderEmailField.visibleProperty().bind(outputDestinationChoiceBox.valueProperty().isEqualTo("Email"));
//        recipientEmailField.managedProperty().bind(outputDestinationChoiceBox.valueProperty().isEqualTo("Email"));
//        recipientEmailField.visibleProperty().bind(outputDestinationChoiceBox.valueProperty().isEqualTo("Email"));
//        emailSubjectField.managedProperty().bind(outputDestinationChoiceBox.valueProperty().isEqualTo("Email"));
//        emailSubjectField.visibleProperty().bind(outputDestinationChoiceBox.valueProperty().isEqualTo("Email"));
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

        // 4. Start processing
        isAborted = false;
        sentStudents.clear();
        new Thread(() -> {
            logger.info("Starting the result sending process in a new thread");
            String outputDestination = "Email";
            try {
                if ("WhatsApp".equals(outputDestination)) {
                    whatsAppService.startService();
                }

                int totalStudents = students.size();
                for (int i = 0; i < totalStudents; i++) {
                    if (isAborted) {
                        logger.warn("Process aborted by user");
                        break;
                    }
                    Student student = students.get(i);
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
                            templateFile
                    );
                    logger.debug("Result image generated: {}", imageFile.getAbsolutePath());

                    // Send message based on destination
                    if ("WhatsApp".equals(outputDestination)) {
                        logger.debug("Sending WhatsApp message to: {}", student.getPhoneNumber());
                        whatsAppService.sendMessage(student.getPhoneNumber(), imageFile);
                        logger.info("Successfully sent WhatsApp message to {}", student.getName());
                    } else if ("Email".equals(outputDestination)) {
                        String recipient = student.getEmail(); // Assuming student has email or using override
                        if (recipient == null || recipient.isEmpty()) {
                            logger.warn("Skipping email for {} as no recipient email found.", student.getName());
                            Platform.runLater(() -> showAlert("Warning", "Skipping email for " + student.getName() + " as no recipient email found."));
                            continue;
                        }
                        String subject = "RAC Result";
                        String sender = "29.abhishek.mittal@gmail.com";
                        logger.debug("Sending email to {} from {} with subject '{}'", recipient, sender, subject);
                        emailService.sendEmailWithAttachment(recipient, sender, subject, "Please find your result attached.", imageFile);
                        logger.info("Successfully sent email to {}", student.getName());
                    }
                    sentStudents.add(student);

                    // Clean up the generated image file
                    if (imageFile.delete()) {
                        logger.debug("Cleaned up image file: {}", imageFile.getName());
                    } else {
                        logger.warn("Could not delete image file: {}", imageFile.getName());
                    }
                }
            } catch (Exception e) {
                logger.error("An error occurred during the result sending process", e);
                showAlert("Error", "An error occurred during processing: " + e.getMessage());
            } finally {
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
            int totalMarks = Integer.parseInt(totalMarksField.getText());
            if (totalMarks <= 0) {
                showAlert("Error", "Total marks must be a positive integer.");
                return false;
            }
        } catch (NumberFormatException e) {
            showAlert("Error", "Total marks must be a valid integer.");
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
