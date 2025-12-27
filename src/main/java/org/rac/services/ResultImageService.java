package org.rac.services;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.web.WebView;
import org.rac.model.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import gui.ava.html.image.generator.HtmlImageGenerator;

public class ResultImageService {

    private static final Logger logger = LoggerFactory.getLogger(ResultImageService.class);

    public File generateImage(Student student, String date, String studentClass, String topic, String heading, String totalMarks, File templateFile) throws IOException {
        logger.info("Generating result image for student: {} , template path: {}", student.getName(), templateFile.toPath());
        String templateContent = new String(Files.readAllBytes(templateFile.toPath()));
        logger.debug("Read HTML template content");

        String populatedContent = templateContent
                .replace("TOPIC_INPUT", topic)
                .replace("DATE_INPUT", date)
                .replace("NAME_INPUT", student.getName())
                .replace("CLASS_INPUT", studentClass)
                .replace("HEADING_INPUT", heading)
                .replace("ADDITIONAL_INPUT", student.getAdditionalDetails())
                .replace("TOTAL_MARKS_INPUT", totalMarks)
                .replace("MARKS_INPUT", String.valueOf(student.getMarksObtained()))
                .replace("MARKS_DEDUCTED_INPUT", String.valueOf(Integer.parseInt(totalMarks) - (int) student.getMarksObtained()));
        logger.debug("Populated HTML template with student data");
        logger.info(totalMarks);
        logger.info(populatedContent);

        String fileName = "result_" + student.getName() + "_" + student.getPhoneNumber() + ".png";
        HtmlImageGenerator htmlImageGenerator = new HtmlImageGenerator();
        htmlImageGenerator.loadHtml(populatedContent);
        htmlImageGenerator.saveAsImage(fileName);
        return new File(fileName);

//        CompletableFuture<File> future = new CompletableFuture<>();
//        Platform.runLater(() -> {
//            try {
//                WebView webView = new WebView();
//                webView.getEngine().loadContent(populatedContent);
//                webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
//                    if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
//                        logger.debug("WebView finished loading content");
//                        WritableImage image = webView.snapshot(new SnapshotParameters(), null);
//                        File file = new File(fileName);
//                        try {
//                            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
//                            logger.info("Successfully generated result image: {}", file.getAbsolutePath());
//                            future.complete(file);
//                        } catch (IOException e) {
//                            logger.error("Failed to write image to file", e);
//                            future.completeExceptionally(e);
//                        }
//                    } else if (newState == javafx.concurrent.Worker.State.FAILED) {
//                        logger.error("WebView failed to load content");
//                        future.completeExceptionally(new RuntimeException("WebView failed to load content"));
//                    }
//                });
//            } catch (Exception e) {
//                logger.error("An error occurred on the JavaFX application thread while generating the image", e);
//                return e;
//            }
//        });

//        try {
//            return future.get();
//        } catch (Exception e) {
//            logger.error("Failed to get result from image generation future", e);
//            throw new IOException("Failed to generate image", e);
//        }
    }
}
