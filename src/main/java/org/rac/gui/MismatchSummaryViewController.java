package org.rac.gui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.rac.services.ExcelReaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class MismatchSummaryViewController {

    private static final Logger logger = LoggerFactory.getLogger(MismatchSummaryViewController.class);

    @FXML private Label mismatchCountLabel;
    @FXML private Label descriptionLabel;
    @FXML private TitledPane mismatchedPane;
    @FXML private TableView<String> mismatchedTableView;
    @FXML private TableColumn<String, String> colMismatchIndex;
    @FXML private TableColumn<String, String> colMismatchName;

    @FXML private TitledPane contactsPane;
    @FXML private TableView<ExcelReaderService.StudentContact> contactsTableView;
    @FXML private TableColumn<ExcelReaderService.StudentContact, String> colContactIndex;
    @FXML private TableColumn<ExcelReaderService.StudentContact, String> colContactName;
    @FXML private TableColumn<ExcelReaderService.StudentContact, String> colContactPhone;

    @FXML private Button openFileButton;

    private Stage stage;
    private File resultsFile;

    public void setup(Stage stage, List<String> mismatchedNames,
                      List<ExcelReaderService.StudentContact> allContacts,
                      File resultsFile, String targetSheetName) {
        this.stage = stage;
        this.resultsFile = resultsFile;

        mismatchCountLabel.setText(mismatchedNames.size() + " name(s) mismatched");
        
        StringBuilder desc = new StringBuilder("Some student names in your results file do not match the names in your contact sheet.");
        if (targetSheetName != null) {
            desc.append("\nTarget Sheet Name: ").append(targetSheetName);
        }
        descriptionLabel.setText(desc.toString());

        // Setup mismatched table
        mismatchedTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        colMismatchIndex.setCellValueFactory(cell -> {
            int idx = mismatchedTableView.getItems().indexOf(cell.getValue()) + 1;
            return new ReadOnlyStringWrapper(String.valueOf(idx));
        });
        colMismatchName.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue()));
        mismatchedTableView.setItems(FXCollections.observableArrayList(mismatchedNames));
        mismatchedPane.setText("Show " + mismatchedNames.size() + " mismatched names");

        // Setup contacts table
        contactsTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        colContactIndex.setCellValueFactory(cell -> {
            int idx = contactsTableView.getItems().indexOf(cell.getValue()) + 1;
            return new ReadOnlyStringWrapper(String.valueOf(idx));
        });
        colContactName.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
        colContactPhone.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getPhone()));
        contactsTableView.setItems(FXCollections.observableArrayList(allContacts));
        contactsPane.setText("Show " + allContacts.size() + " available contacts");

        if (resultsFile == null || !resultsFile.exists()) {
            openFileButton.setVisible(false);
            openFileButton.setManaged(false);
        }
    }

    @FXML
    public void handleOpenFile() {
        if (resultsFile != null && resultsFile.exists()) {
            new Thread(() -> {
                try {
                    logger.info("Opening results file for editing: {}", resultsFile.getAbsolutePath());
                    Desktop.getDesktop().open(resultsFile);
                } catch (Exception e) {
                    logger.error("Failed to open results file", e);
                }
            }, "results-file-opener").start();
        }
    }

    @FXML
    public void handleClose() {
        stage.close();
    }

    public static void show(List<String> mismatchedNames,
                            List<ExcelReaderService.StudentContact> allContacts,
                            File resultsFile, String targetSheetName) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                MismatchSummaryViewController.class.getResource("/org/rac/gui/MismatchSummaryView.fxml"));
        Parent root = loader.load();
        MismatchSummaryViewController controller = loader.getController();

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Validation Mismatches Summary");
        stage.setResizable(true);
        stage.setMinWidth(580);
        stage.setMinHeight(400);
        stage.setScene(new Scene(root));
        stage.setWidth(640);
        stage.setHeight(650);

        controller.setup(stage, mismatchedNames, allContacts, resultsFile, targetSheetName);
        stage.showAndWait();
    }
}
