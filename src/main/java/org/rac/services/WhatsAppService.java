package org.rac.services;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;

/**
 * This service is responsible for automating WhatsApp Web using Selenium.
 * It is a fragile solution and may break if WhatsApp changes its web interface.
 */
public class WhatsAppService {

    private static final Logger logger = LoggerFactory.getLogger(WhatsAppService.class);
    private WebDriver driver;

    /**
     * Starts the Selenium WebDriver and opens WhatsApp Web.
     *
     * IMPORTANT: This method requires manual configuration by the user.
     *
     * 1.  Download ChromeDriver:
     *     - You need to download the ChromeDriver that matches your version of Google Chrome.
     *     - Download from: https://chromedriver.chromium.org/downloads
     *     - Place the `chromedriver.exe` file in a known location on your computer.
     *
     * 2.  Chrome User Profile:
     *     - To avoid scanning the WhatsApp QR code every time you run the application, you can use an existing Chrome user profile.
     *     - Find your Chrome profile path by navigating to `chrome://version` in your Chrome browser and looking for "Profile Path".
     *
     * 3.  Update the paths below:
     *     - The `webdriver.chrome.driver` system property must be set to the absolute path of your `chromedriver.exe`.
     *     - The `user-data-dir` argument must be set to the absolute path of your Chrome profile.
     *
     * TODO: In a future version, these paths should be made configurable through the application's UI.
     */
    public void startService() {
        try {
            logger.info("Starting WhatsApp service...");
            // Read from system properties, fall back to hardcoded values if not set
            String chromeDriverPath = System.getProperty("webdriver.chrome.driver", "C:/Users/Abhishek/RAC_Projects/RACOperationalApp/chromedriver.exe");
            String chromeProfilePath = System.getProperty("chrome.user.data.dir", "C:/Users/Abhishek/AppData/Local/Google/Chrome/User Data/Profile");

            logger.info("Using ChromeDriver at: {}", chromeDriverPath);
            logger.info("Using Chrome profile at: {}", chromeProfilePath);

            System.setProperty("webdriver.chrome.driver", chromeDriverPath);
            ChromeOptions options = new ChromeOptions();
            options.addArguments("user-data-dir=" + chromeProfilePath);
            options.addArguments("--disable-blink-features=AutomationControlled"); // Tries to hide automation flags
            options.addArguments("--disable-features=EnableEphemeralFlashPermission"); // Disables ephemeral flash permission
            options.addArguments("--disable-features=AutoplayIgnoresWebAudio"); // Disables autoplay ignores web audio
            options.addArguments("--no-sandbox"); // WARNING: This is a security risk, but sometimes necessary for CI/CD or certain environments
            options.addArguments("--disable-dev-shm-usage"); // Overcomes limited resource problems
            options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"}); // Disables info bar
            options.setExperimentalOption("useAutomationExtension", false); // Disables automation extension
            driver = new ChromeDriver(options);
            logger.info("Navigating to https://web.whatsapp.com");
            driver.get("https://web.whatsapp.com");
            logger.info("WhatsApp service started successfully. Please scan the QR code if this is the first run.");
        } catch (Exception e) {
            logger.error("Failed to start WhatsApp service. Please check your ChromeDriver and Chrome profile paths.", e);
            throw new RuntimeException("Failed to start WhatsApp service", e);
        }
    }

    /**
     * Sends a message with an image to a given phone number.
     * @param phoneNumber The phone number to send the message to (including country code, without '+' or '00').
     * @param imageFile The image file to send.
     */
    public void sendMessage(String phoneNumber, File imageFile) {
        logger.info("Attempting to send image message to {}", phoneNumber);
        try {
            String url = "https://web.whatsapp.com/send?phone=+91" + phoneNumber;
            logger.debug("Navigating to: {}", url);
            driver.get(url);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

            // Wait for the chat to load and the attach button to be clickable
            logger.debug("Waiting for attach button to be clickable");
            WebElement attachButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("div[title='Attach']")));
            attachButton.click();
            logger.debug("Attach button clicked");

            // Wait for the image input to be present
            logger.debug("Waiting for image input to be present");
            WebElement imageInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[accept='image/*,video/mp4,video/3gpp,video/quicktime']")));
            logger.debug("Attaching image file: {}", imageFile.getAbsolutePath());
            imageInput.sendKeys(imageFile.getAbsolutePath());

            // Wait for the send button to be clickable
            logger.debug("Waiting for send button to be clickable");
            WebElement sendButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("span[data-icon='send']")));
            sendButton.click();
            logger.debug("Send button clicked");

            // A short delay to ensure the message is sent before moving to the next one.
            logger.debug("Waiting for 2 seconds for message to be sent...");
            Thread.sleep(2000);

            logger.info("Image message sent successfully to {}", phoneNumber);
        } catch (Exception e) {
            logger.error("Failed to send image message to {}", phoneNumber, e);
            // Re-throw the exception to be handled by the controller
            throw new RuntimeException("Failed to send image message to " + phoneNumber, e);
        }
    }

    /**
     * Sends a text message to a given phone number.
     * @param phoneNumber The phone number to send the message to (including country code, without '+' or '00').
     * @param messageText The text message to send.
     */
    public void sendTextMessage(String phoneNumber, String messageText) {
        logger.info("Attempting to send text message to {}", phoneNumber);
        try {
            String url = "https://web.whatsapp.com/send?phone=+91" + phoneNumber;
            logger.debug("Navigating to: {}", url);
            driver.get(url);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

            // Wait for the message input box to be present
            logger.debug("Waiting for message input box to be present");
            // This XPath selects the contenteditable div which is the message input box
            WebElement messageInputBox = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@contenteditable='true'][@data-tab='10']")));
            
            logger.debug("Typing message: '{}' into input box", messageText);
            messageInputBox.sendKeys(messageText);
            messageInputBox.sendKeys(Keys.ENTER); // Send the message by pressing Enter
            
            logger.debug("Waiting for 2 seconds for message to be sent...");
            Thread.sleep(2000); // Give some time for the message to be sent

            logger.info("Text message sent successfully to {}", phoneNumber);
        } catch (Exception e) {
            logger.error("Failed to send text message to {}", phoneNumber, e);
            throw new RuntimeException("Failed to send text message to " + phoneNumber, e);
        }
    }

    /**
     * Quits the WebDriver and closes the browser.
     */
    public void stopService() {
        if (driver != null) {
            logger.info("Stopping WhatsApp service");
            try {
                driver.quit();
            } catch (Exception e) {
                logger.error("An error occurred while stopping the WhatsApp service", e);
            }
            logger.info("WhatsApp service stopped");
        }
    }
}
