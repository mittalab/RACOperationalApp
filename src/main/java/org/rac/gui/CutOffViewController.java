package org.rac.gui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.rac.model.Student;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CutOffViewController {

    @FXML private TextArea  distributionArea;
    @FXML private TextField cutOffField;

    private Stage  stage;
    private Double result = null;

    void setup(Stage stage, List<Student> students) {
        this.stage = stage;

        Map<Double, Integer> dist = new TreeMap<>(Collections.reverseOrder());
        for (Student s : students) dist.merge(s.getMarksObtained(), 1, Integer::sum);

        StringBuilder sb = new StringBuilder("Total Students: ")
                .append(students.size()).append("\n\nMarks Distribution:\n");
        for (Map.Entry<Double, Integer> e : dist.entrySet())
            sb.append(String.format("  %.1f Marks : %d student(s)%n", e.getKey(), e.getValue()));

        distributionArea.setText(sb.toString());
    }

    @FXML
    public void handleOk() {
        String text = cutOffField.getText().trim();
        try {
            result = Double.parseDouble(text);
            stage.close();
        } catch (NumberFormatException e) {
            MessageDialogViewController.showError("Invalid Input", "Invalid cut-off marks. Please enter a valid number.");
        }
    }

    @FXML
    public void handleCancel() {
        stage.close();
    }

    public static Double show(List<Student> students) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                CutOffViewController.class.getResource("/org/rac/gui/CutOffView.fxml"));
        Parent root = loader.load();
        CutOffViewController controller = loader.getController();

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Set Cut-off for Toppers");
        stage.setResizable(true);
        stage.setScene(new Scene(root));

        controller.setup(stage, students);
        stage.showAndWait();
        return controller.result;
    }
}
