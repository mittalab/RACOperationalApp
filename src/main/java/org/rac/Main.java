package org.rac;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Main extends Application {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        logger.info("Starting RAC Operational App");
        primaryStage = stage;
        primaryStage.setTitle("RAC Operational App");
        showMainView();
    }

    public static void showMainView() throws IOException {
        logger.info("Showing Main View");
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/org/rac/gui/MainView.fxml"));
        Parent root = loader.load();
        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.show();
    }

    public static void showActivityView(String fxmlPath) throws IOException {
        logger.info("Showing Activity View: {}", fxmlPath);
        FXMLLoader loader = new FXMLLoader(Main.class.getResource(fxmlPath));
        Parent root = loader.load();
        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() throws Exception {
        logger.info("Stopping RAC Operational App");
        super.stop();
    }
}
