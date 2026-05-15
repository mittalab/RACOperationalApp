package org.rac.gui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import org.rac.Main;
import org.rac.model.Activity;
import org.rac.services.ActivityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainViewController {

    private static final Logger logger = LoggerFactory.getLogger(MainViewController.class);

    @FXML
    private VBox navVBox;

    private final ActivityService activityService = new ActivityService();
    private final List<Button> navButtons = new ArrayList<>();
    private Button homeButton;

    @FXML
    public void initialize() {
        logger.info("Initializing MainViewController");

        homeButton = createNavButton("⊞  Home", true);
        navVBox.getChildren().add(homeButton);

        for (Activity activity : activityService.getActivities()) {
            Button btn = createNavButton("↑  " + activity.getName(), false);
            btn.setOnAction(e -> navigateTo(activity, btn));
            navVBox.getChildren().add(btn);
            navButtons.add(btn);
        }

        logger.info("Loaded {} activities in sidebar", navButtons.size());
    }

    private Button createNavButton(String label, boolean active) {
        Button btn = new Button(label);
        btn.getStyleClass().add("sidebar-nav-item");
        if (active) btn.getStyleClass().add("sidebar-nav-item-active");
        btn.setMaxWidth(Double.MAX_VALUE);
        return btn;
    }

    private void navigateTo(Activity activity, Button activeBtn) {
        logger.info("Navigating to activity: {}", activity.getName());
        try {
            Main.showActivityView(activity.getFxmlPath());
        } catch (IOException e) {
            logger.error("Failed to load activity view: {}", activity.getFxmlPath(), e);
        }
    }
}
