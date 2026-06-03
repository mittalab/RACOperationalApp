package org.rac.gui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.collections.FXCollections;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.rac.model.Student;

import java.io.IOException;
import java.util.List;

public class ConfirmationViewController {

    @FXML private HBox studentCountRow;
    @FXML private Label studentCountLabel;
    @FXML private Label whatsAppLabel;
    @FXML private TitledPane studentListPane;
    @FXML private TableView<Student> studentTableView;
    @FXML private TableColumn<Student, String> colIndex;
    @FXML private TableColumn<Student, String> colName;
    @FXML private TableColumn<Student, String> colPhone;
    @FXML private HBox adminMessagesRow;
    @FXML private VBox adminMessagesList;

    private Stage stage;
    private boolean confirmed = false;

    void setup(Stage stage, List<Student> students, boolean sendWA,
               boolean showStudentSection, List<String> adminMessages) {
        this.stage = stage;
        whatsAppLabel.setText(sendWA ? "Yes — results will be sent on WhatsApp" : "No — images only, no WhatsApp messages");
        whatsAppLabel.getStyleClass().setAll(sendWA ? "dialog-wa-yes-label" : "dialog-wa-no-label");

        if (showStudentSection) {
            studentCountLabel.setText(students.size() + " student(s) will be processed.");

            studentTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

            colIndex.setCellValueFactory(cell -> {
                int idx = studentTableView.getItems().indexOf(cell.getValue()) + 1;
                return new ReadOnlyStringWrapper(String.valueOf(idx));
            });
            colName.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
            colPhone.setCellValueFactory(cell -> {
                String phone = cell.getValue().getPhone();
                if (!sendWA || phone == null || phone.isBlank()) return new ReadOnlyStringWrapper("—");
                return new ReadOnlyStringWrapper(phone);
            });

            colPhone.setVisible(sendWA);

            studentTableView.setItems(FXCollections.observableArrayList(students));
            studentListPane.setText("Show " + students.size() + " students");
        } else {
            studentCountRow.setVisible(false);
            studentCountRow.setManaged(false);
            studentListPane.setVisible(false);
            studentListPane.setManaged(false);
        }

        if (!adminMessages.isEmpty()) {
            adminMessagesRow.setVisible(true);
            adminMessagesRow.setManaged(true);
            for (String msg : adminMessages) {
                Label lbl = new Label("• " + msg);
                lbl.getStyleClass().add("dialog-highlight-label");
                adminMessagesList.getChildren().add(lbl);
            }
        }
    }

    @FXML
    public void handleOk() {
        confirmed = true;
        stage.close();
    }

    @FXML
    public void handleCancel() {
        stage.close();
    }

    public static boolean show(List<Student> students, boolean sendWA,
                               boolean showStudentSection, List<String> adminMessages) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                ConfirmationViewController.class.getResource("/org/rac/gui/ConfirmationView.fxml"));
        Parent root = loader.load();
        ConfirmationViewController controller = loader.getController();

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Confirmation");
        stage.setResizable(true);
        stage.setMinWidth(560);
        stage.setMinHeight(300);
        stage.setScene(new Scene(root));
        stage.setWidth(600);
        stage.setHeight(700);

        controller.setup(stage, students, sendWA, showStudentSection, adminMessages);
        stage.showAndWait();
        return controller.confirmed;
    }
}
