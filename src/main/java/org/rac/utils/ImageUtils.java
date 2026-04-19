package org.rac.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

public class ImageUtils {

    private static final Logger logger = LoggerFactory.getLogger(ImageUtils.class);

    public static String getBase64EncodedImage(String resourcePath) {
        try (InputStream is = ImageUtils.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                logger.error("Image resource not found: {}", resourcePath);
                return "";
            }
            byte[] imageBytes = is.readAllBytes();
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            logger.debug("Successfully base64 encoded image from resource: {}", resourcePath);
            return "data:image/png;base64," + base64; // Assuming PNG, can be made dynamic
        } catch (IOException e) {
            logger.error("Failed to read or encode image from resource: {}", resourcePath, e);
            return "";
        }
    }
}
