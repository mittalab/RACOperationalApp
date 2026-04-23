# GEMINI.md - RAC Operational App

This document provides context and instructions for AI agents working on the RAC Operational App.

## Project Overview

The **RAC Operational App** is a JavaFX-based desktop application designed to automate operational tasks for a coaching center. Its primary current feature is automating the delivery of student test results to parents via WhatsApp (using Selenium) and Email (using JavaMail).

### Core Technologies
- **Language:** Java 25 (Targeting JDK 25)
- **UI Framework:** JavaFX 25 (with FXML)
- **Automation:** 
    - **Selenium:** Used for WhatsApp Web automation (message delivery).
    - **Playwright:** Used for rendering HTML templates into high-quality PNG result images.
- **Data Handling:** Apache POI for reading/writing Excel files (`.xlsx`).
- **Rendering:** Uses HTML/CSS templates for result cards.
- **Logging:** SLF4J with Logback.

---

## Architecture & Directory Structure

The project follows a standard MVC-inspired architecture for JavaFX.

- `src/main/java/org/rac/`
    - `NewMain.java`: Main entry point (workaround for JavaFX/JAR issues).
    - `Main.java`: JavaFX Application class, handles stage and scene transitions.
    - `gui/`: FXML Controllers (e.g., `MainViewController`, `SendResultsViewController`).
    - `model/`: Data objects (e.g., `Student`) and activity abstractions.
    - `services/`: Business logic.
        - `WhatsAppService`: Selenium automation for WhatsApp.
        - `ResultImageService`: Playwright rendering logic.
        - `ExcelReaderService`: Apache POI Excel parsing.
        - `EmailService`: JavaMail implementation.
    - `utils/`: Common helpers (e.g., `ImageUtils` for Base64 encoding).
- `src/main/resources/`
    - FXML files for UI layouts.
    - HTML templates for result images (`result_template.html`).
    - Static assets (icons, signatures, headers).

---

## Building and Running

### Build Commands
```bash
# Clean and package the application into a fat JAR
mvn clean package

# Run the application directly using Maven
mvn javafx:run
```

### Running the Executable
```bash
# Run the generated JAR
java -jar target/RACOperationalApp-1.0.0.jar
```

### Creating a Windows Executable (jpackage)
```bash
jpackage --name RACOperationalApp --input target --main-jar RACOperationalApp-1.0.0.jar --main-class org.rac.NewMain
```

---

## Critical Configurations & Setup

### 1. WhatsApp Automation (Selenium)
The application requires a local `chromedriver.exe` and a Chrome User Profile to bypass WhatsApp QR login after the first session.
- **File:** `src/main/java/org/rac/services/WhatsAppService.java`
- **Settings:** `webdriver.chrome.driver` and `user-data-dir` arguments. 
- **User Hint:** These are currently hardcoded to specific local paths and must be updated for different environments.

### 2. Image Rendering (Playwright)
Playwright is used for "headless" rendering of result cards.
- **Storage:** It expects browsers in the `ms-playwright` directory (configured in `Main.java`).
- **Template:** Placeholders in `result_template.html` (e.g., `NAME_INPUT`, `MARKS_INPUT`) are replaced dynamically.

### 3. Email Fallback
- **File:** `EmailService.java`
- **Requirement:** Requires a valid Gmail address and an App-specific Password.

---

## Development Conventions

- **UI:** Prefer FXML for layouts. All controllers should reside in the `gui` package.
- **Threading:** Heavy operations (sending messages, rendering images) **must** run on a background thread (e.g., using `Task` or `Thread`) to avoid freezing the JavaFX UI.
- **Logging:** Use `logger.info()`, `logger.debug()`, and `logger.error()` consistently. Logs are written to `rac-operational-app.log`.
- **Validation:** Always validate student data (phone numbers, marks) before starting a batch process.

---

## TODOs & Future Improvements
- [ ] Make ChromeDriver and Chrome Profile paths configurable via a "Settings" screen.
- [ ] Move hardcoded credentials to a secure local configuration file or environment variables.
- [ ] Implement automated tests for Excel parsing and image generation logic.
- [ ] Optimize Playwright browser management (check for existence before initializing).
