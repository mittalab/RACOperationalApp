package org.rac.gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import org.rac.Main;
import org.rac.model.Activity;
import org.rac.services.ActivityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MainViewController {

    private static final Logger logger = LoggerFactory.getLogger(MainViewController.class);

    @FXML
    private VBox activityVBox;

    @FXML
    private Button selectActivityButton;

    private ToggleGroup activityToggleGroup;

    private final ActivityService activityService = new ActivityService();

    @FXML
    public void initialize() {
        logger.info("Initializing MainViewController");
        activityToggleGroup = new ToggleGroup();
        ObservableList<Activity> activities = FXCollections.observableArrayList(activityService.getActivities());

        for (Activity activity : activities) {
            RadioButton radioButton = new RadioButton(activity.getName());
            radioButton.setUserData(activity);
            radioButton.setToggleGroup(activityToggleGroup);
            activityVBox.getChildren().add(radioButton);
        }

        if (!activities.isEmpty()) {
            activityToggleGroup.selectToggle(activityToggleGroup.getToggles().get(0));
        }

        logger.info("Loaded {} activities as radio buttons", activities.size());
    }

    @FXML
    public void handleSelectActivity() {
        Toggle selectedToggle = activityToggleGroup.getSelectedToggle();
        if (selectedToggle != null) {
            Activity selectedActivity = (Activity) selectedToggle.getUserData();
            logger.info("Activity selected: {}", selectedActivity.getName());
            try {
                Main.showActivityView(selectedActivity.getFxmlPath());
            } catch (IOException e) {
                logger.error("Failed to load activity view: {}", selectedActivity.getFxmlPath(), e);
                // Handle error (e.g., show an alert)
            }
        } else {
            logger.warn("No activity selected.");
            // Optionally, show an alert to the user.
        }
    }
}
