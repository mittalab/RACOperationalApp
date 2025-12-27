package org.rac.gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseEvent;
import org.rac.Main;
import org.rac.model.Activity;
import org.rac.services.ActivityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MainViewController {

    private static final Logger logger = LoggerFactory.getLogger(MainViewController.class);

    @FXML
    private ListView<Activity> activityListView;

    private final ActivityService activityService = new ActivityService();

    @FXML
    public void initialize() {
        logger.info("Initializing MainViewController");
        ObservableList<Activity> activities = FXCollections.observableArrayList(activityService.getActivities());
        activityListView.setItems(activities);
        logger.info("Loaded {} activities", activities.size());
    }

    @FXML
    public void handleActivitySelection(MouseEvent event) {
        Activity selectedActivity = activityListView.getSelectionModel().getSelectedItem();
        if (selectedActivity != null) {
            logger.info("Activity selected: {}", selectedActivity.getName());
            try {
                Main.showActivityView(selectedActivity.getFxmlPath());
            } catch (IOException e) {
                logger.error("Failed to load activity view: {}", selectedActivity.getFxmlPath(), e);
                // Handle error (e.g., show an alert)
            }
        }
    }
}
