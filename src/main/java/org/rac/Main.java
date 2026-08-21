package org.rac;

import com.microsoft.playwright.Playwright;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
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
        primaryStage.getIcons().add(new Image(Main.class.getResourceAsStream("/app_icon.png")));
        primaryStage.setMaximized(true);
        showMainView();
    }

    public static void showMainView() throws IOException {
        logger.info("Showing Main View");
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/org/rac/gui/MainView.fxml"));
        Parent root = loader.load();
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    public static void showActivityView(String fxmlPath) throws IOException {
        logger.info("Showing Activity View: {}", fxmlPath);
        FXMLLoader loader = new FXMLLoader(Main.class.getResource(fxmlPath));
        Parent root = loader.load();
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }


    public static void main(String[] args) {
        System.setProperty("PLAYWRIGHT_BROWSERS_PATH", "ms-playwright");
        Playwright.create();
        launch(args);
    }

    @Override
    public void stop() throws Exception {
        logger.info("Stopping RAC Operational App");
        super.stop();
    }
}
