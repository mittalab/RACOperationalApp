package org.rac.services;

import com.aspose.html.HTMLDocument;
import com.aspose.html.converters.Converter;
import com.aspose.html.rendering.image.ImageFormat;
import com.aspose.html.saving.ImageSaveOptions;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.web.WebView;
import org.openqa.selenium.Point;
import org.rac.model.Student;
import org.rac.utils.ImageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
//import gui.ava.html2image.HtmlImageGenerator;
//import gui.ava.html.image.generator.HtmlImageGenerator;
import org.xhtmlrenderer.swing.Java2DRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import com.microsoft.playwright.*;

import java.nio.file.Paths;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;


public class ResultImageService {

    private static final Logger logger = LoggerFactory.getLogger(ResultImageService.class);

    private static String formatMarks(double marks) {
        if (marks == Math.floor(marks) && !Double.isInfinite(marks)) {
            return String.valueOf((int) marks);
        }
        return String.valueOf(marks);
    }

    public void generateTopperImage(List<Student> toppers, String date, String studentClass, String batch, String topic, String totalMarks, File templateFile, File htmlDirs, File pngDirs) throws IOException {
        logger.info("Generating topper list image for {} toppers", toppers.size());
        String templateContent = new String(Files.readAllBytes(templateFile.toPath()));

        StringBuilder tableRows = new StringBuilder();
        int sno = 1;
        for (Student topper : toppers) {
            tableRows.append("<tr>")
                    .append("<td>").append(sno++).append(".</td>")
                    .append("<td>").append(topper.getName()).append("</td>")
                    .append("<td>").append(date).append("</td>")
                    .append("<td>").append(topic.toUpperCase()).append("</td>")
                    .append("<td class='marks'>").append(formatMarks(topper.getMarksObtained())).append("/").append(totalMarks).append("</td>")
                    .append("</tr>");
        }

        String populatedContent = templateContent
                .replace("CLASS_BATCH_INPUT", "CLASS -" + studentClass.toUpperCase() + " (" + batch.toUpperCase() + ")")
                .replace("TABLE_ROWS_INPUT", tableRows.toString())
                .replace("LOGO_IMAGE", ImageUtils.getBase64EncodedImage("/app_icon.png"))
                .replace("SIGNATURE_IMAGE", ImageUtils.getBase64EncodedImage("/signature.png"));

        String fileNamePNG = "Toppers_List.png";
        String fileNameHTML = "toppers_list.html";

        File fileHTML = new File(htmlDirs, fileNameHTML);
        try (java.io.FileWriter fileWriter = new java.io.FileWriter(fileHTML)) {
            fileWriter.write(populatedContent);
        }

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.navigate(fileHTML.toURI().toString());
            Locator element = page.locator(".container");
            element.screenshot(new Locator.ScreenshotOptions().setPath(Paths.get(pngDirs.getAbsolutePath(), fileNamePNG)));
            browser.close();
        } catch (Exception e) {
            logger.error("Error during playwright topper image generation", e);
        }
    }

    public File generateAbsentImage(List<String> absentNames, String date, String studentClass,
            String batch, String topic, File templateFile, File htmlDirs, File pngDirs) throws IOException {
        logger.info("Generating absent notice image for {} students", absentNames.size());
        String templateContent = new String(Files.readAllBytes(templateFile.toPath()));

        StringBuilder studentRows = new StringBuilder();
        int sno = 1;
        for (String name : absentNames) {
            studentRows.append("<div class='student-row'>")
                    .append("<div class='sno'>").append(sno++).append("</div>")
                    .append("<span class='student-name'>").append(name).append("</span>")
                    .append("<span class='absent-badge'>ABSENT</span>")
                    .append("</div>");
        }

        String populatedContent = templateContent
                .replace("DATE_INPUT", date)
                .replace("CLASS_INPUT", studentClass.toUpperCase())
                .replace("BATCH_INPUT", batch)
                .replace("TOPIC_INPUT", topic.toUpperCase())
                .replace("STUDENT_ROWS_INPUT", studentRows.toString())
                .replace("LOGO_IMAGE", ImageUtils.getBase64EncodedImage("/app_icon.png"))
                .replace("WATERMARK_IMAGE", ImageUtils.getBase64EncodedImage("/watermark.png"));

        File fileHTML = new File(htmlDirs, "absent_notice.html");
        try (java.io.FileWriter fw = new java.io.FileWriter(fileHTML)) {
            fw.write(populatedContent);
        }

        File pngFile = new File(pngDirs, "Absent_Notice.png");
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.navigate(fileHTML.toURI().toString());
            page.locator(".container").screenshot(
                    new Locator.ScreenshotOptions().setPath(Paths.get(pngFile.getAbsolutePath())));
            browser.close();
        } catch (Exception e) {
            logger.error("Error during playwright absent image generation", e);
        }
        return pngFile;
    }

    public File generateImage(Student student, String date, String studentClass, String topic, String heading, String totalMarks, File templateFile, int index, File htmlDirs, File pngDirs) throws IOException {
        logger.info("Generating result image for student: {} , template path: {}", student.getName(), templateFile.toPath());
        String templateContent = new String(Files.readAllBytes(templateFile.toPath()));
        logger.info("Read HTML template content");

        // Dynamically embed header and signature images
        String headerImageBase64 = ImageUtils.getBase64EncodedImage("/header.png");
        String logoImageBase64 = ImageUtils.getBase64EncodedImage("/app_icon.png");
        String signatureImageBase64 = ImageUtils.getBase64EncodedImage("/signature.png");
        String watermarkImageBase64 = ImageUtils.getBase64EncodedImage("/watermark.png");

//        templateContent = templateContent.replace("src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=\" alt=\"Header\"", "src=\"" + headerImageBase64 + "\" alt=\"Header\"");
//        templateContent = templateContent.replace("src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=\" alt=\"Signature\"", "src=\"" + signatureImageBase64 + "\" alt=\"Signature\"");

        String populatedContent = templateContent
                .replace("TOPIC_INPUT", topic.toUpperCase())
                .replace("DATE_INPUT", date)
                .replace("NAME_INPUT", student.getName())
                .replace("CLASS_INPUT", studentClass.toUpperCase())
                .replace("HEADING_INPUT", heading.toUpperCase())
                .replace("ADDITIONAL_INPUT", student.getAdditionalDetails())
                .replace("TOTAL_MARKS_INPUT", totalMarks)
                .replace("MARKS_INPUT", formatMarks(student.getMarksObtained()))
                .replace("SIGNATURE_IMAGE", signatureImageBase64)
                .replace("HEADER_IMAGE", headerImageBase64)
                .replace("WATERMARK_IMAGE", watermarkImageBase64)
                .replace("LOGO_IMAGE", logoImageBase64)
                .replace("MARKS_DEDUCTED_INPUT", formatMarks(Double.parseDouble(totalMarks) - student.getMarksObtained()));
        logger.info("Populated HTML template with student data");
        logger.info(totalMarks);
        //logger.info(populatedContent);


        String baseName = "result_" + student.getName() + "_" + student.getPhone() + "_" + index;
        String fileNamePNG = baseName + ".png";
        String fileNameHTML = baseName + ".html";

        // Write the image to a file

        File file = new File(htmlDirs, fileNameHTML);
        try (java.io.FileWriter fileWriter = new java.io.FileWriter(file)) {
            fileWriter.write(populatedContent);
            logger.info("Generated HTML content written for debugging: {}", fileNameHTML);
        }


//        File fileHTML = new File(fileNameHTML);
//        try {
//            Files.write(fileHTML.toPath(), populatedContent.getBytes());
//            logger.info("Generated HTML content written for debugging: {}", fileHTML.getAbsolutePath());
//        } catch (IOException e) {
//            logger.error("Failed to write populatedContent to abc.html", e);
//        }

        File pngFile = new File(pngDirs, fileNamePNG);

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true)
            );

            Page page = browser.newPage();

            page.navigate(file.toURI().toString());

            // Screenshot only container
            Locator element = page.locator(".container");

            element.screenshot(new Locator.ScreenshotOptions()
                    .setPath(Paths.get(pngDirs.getAbsolutePath(), fileNamePNG)));

            browser.close();
        } catch (Exception e) {
            logger.error("Error during playwright", e);
        }

        return pngFile;

        // Initialize an HTML document from the file
        // Set Chrome to headless
//        ChromeOptions options = new ChromeOptions();
//        options.addArguments("--headless=new");
//        options.addArguments("--window-size=1000,1400");
//
//        WebDriver driver = new ChromeDriver(options);
//
//        // Load local HTML file
//        String path = fileNameHTML;
//        driver.get(path);
//
//        // Wait for rendering
//        try {
//            Thread.sleep(2000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//
//        // Take full screenshot
//        File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
//        BufferedImage fullImg = ImageIO.read(screenshot);
//
//        // Locate the container (black boundary div)
//        WebElement element = driver.findElement(By.className("container"));
//
//        Point point = element.getLocation();
//        int width = element.getSize().getWidth();
//        int height = element.getSize().getHeight();
//
//        // Crop image to container
//        BufferedImage croppedImg = fullImg.getSubimage(
//                point.getX(),
//                point.getY(),
//                width,
//                height
//        );
//
//        // Save output
//        ImageIO.write(croppedImg, "png", new File(fileNamePNG));
//
//        driver.quit();
//
//        System.out.println("Image generated successfully!");
//        return null;

//        HTMLDocument document = new HTMLDocument(fileNameHTML);
//        logger.info(document.getBody().toString());
//        // Initialize ImageSaveOptions
//        ImageSaveOptions options = new ImageSaveOptions(ImageFormat.Jpeg);
//
//        // Convert HTML to PNG
//        Converter.convertHTML(document, options, fileNamePNG);
//        File filePNG = new File(fileNamePNG);
//        return filePNG;

//        HtmlImageGenerator htmlImageGenerator = new HtmlImageGenerator();
//        htmlImageGenerator.loadHtml(populatedContent);
//        htmlImageGenerator.saveAsImage(fileName);
//        return new File(fileName);

//        Document document = Jsoup.parse(populatedContent);
//
//        // 2. Set output settings to XML syntax for XHTML compliance
//        document.outputSettings()
//                .syntax(Document.OutputSettings.Syntax.xml) // Closes all tags (e.g., <br />)
//                .escapeMode(Entities.EscapeMode.xhtml)      // Uses XHTML entities
//                .charset("UTF-8");
//
//        // 3. Return the cleaned XHTML as a String
//        String finalXHTML = document.html();
//        Files.write(new File("abc_2.html").toPath(), finalXHTML.getBytes());


//        int width = 5000;
//        int height = 5000;
//        Java2DRenderer renderer = new Java2DRenderer(populatedContent, width, height);
//        BufferedImage image = renderer.getImage();
//
//        ImageIO.write(image, "png", file);
//        logger.info("Publishing file {}", file.getAbsolutePath());

//
//        JLabel label = new JLabel(populatedContent);
//        label.setSize(200, 120);
//
//        BufferedImage image = new BufferedImage(
//                label.getWidth(), label.getHeight(),
//                BufferedImage.TYPE_INT_ARGB);
//
//        {
//            // paint the html to an image
//            Graphics g = image.getGraphics();
//            g.setColor(Color.BLACK);
//            label.paint(g);
//            g.dispose();
//        }
//
//        ImageIO.write(image, "png", file);
//        CompletableFuture<File> future = new CompletableFuture<>();
//        return file;
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
