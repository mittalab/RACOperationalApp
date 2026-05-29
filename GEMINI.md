# GEMINI.md - RAC Operational App

This document provides context and instructions for AI agents working on the RAC Operational App.

## Project Overview

The **RAC Operational App** is a JavaFX-based desktop application designed to automate operational tasks for a coaching center. Its primary features include automating the delivery of student test results and tracking message delivery statuses.

### Core Technologies
- **Language:** Java 25
- **UI Framework:** JavaFX 25.0.1 (with FXML)
- **Automation & APIs:**
    - **Meta WhatsApp Cloud API:** Primary method for WhatsApp message delivery (replacing legacy Selenium automation).
    - **Custom Status Webhook:** Tracks real-time delivery status (Sent, Delivered, Read, Failed).
    - **Playwright:** Used for rendering HTML templates into high-quality PNG result images, topper lists, and absent notices.
    - **JavaMail (Jakarta Mail):** Fallback/complementary method for Email delivery.
- **Data Handling:** Apache POI (5.2.2) for reading/writing Excel files (`.xlsx`).
- **Logging:** SLF4J with Logback.

---

## Architecture & Directory Structure

The project follows a standard MVC-inspired architecture for JavaFX.

- `src/main/java/org/rac/`
    - `NewMain.java`: Main entry point (workaround for JavaFX/JAR issues).
    - `Main.java`: JavaFX Application class, handles stage and scene transitions.
    - `gui/`: FXML Controllers.
        - `MainViewController`: Sidebar and navigation management.
        - `SendResultsViewController`: Handles the result delivery workflow.
        - `DeliveryTrackerViewController`: Real-time tracking of message statuses.
        - `CheckWamidStatusViewController`: Manual WAMID status lookup.
    - `model/`: Data objects and activity abstractions.
        - `Student`, `RunRecord`, `MessageDelivery`.
        - `Activity` hierarchy for sidebar navigation.
    - `services/`: Business logic.
        - `WhatsAppApiService`: Meta Cloud API integration with retry logic and media upload.
        - `WamidStatusService`: Batch checking of message statuses via custom webhook.
        - `ResultImageService`: Playwright rendering logic for various templates.
        - `ExcelReaderService`: Multi-format Excel parsing.
        - `EmailService`: JavaMail implementation.
    - `utils/`: Common helpers (e.g., `ImageUtils` for Base64 encoding).
- `src/main/resources/`
    - FXML files for UI layouts.
    - HTML templates for result cards (`result_template.html`), toppers (`topper_template.html`), and absent notices (`absent_template.html`).
    - Static assets (icons, signatures, headers).

---

## Key Features

1.  **Test Result Delivery:** Automates sending personalized result cards to parents via WhatsApp and Email.
2.  **Centralized Contact Sync:** Automatically fetches the latest student phone numbers from a master Google Spreadsheet (via `GoogleDriveService`). This ensures all app installations use the same updated data.
3.  **Delivery Tracking:** Provides a real-time "Delivery Tracker" view.
3.  **Topper & Absent Lists:** Generates visual summaries for toppers and absent students from the test data.
4.  **WAMID Management:** Allows tracking and manual status checking of WhatsApp message IDs (WAMIDs).
5.  **Excel Reporting:** Generates "Run Reports" and "Abort Reports" in Excel format to document the outcome of delivery batches.

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

---

## Critical Configurations & Setup

### 1. WhatsApp Cloud API
Configuration is currently managed in `WhatsAppApiService.java`.
- `PHONE_ID`: The Meta Phone Number ID.
- `BEARER_TOKEN`: Permanent access token for the WhatsApp Cloud API.

### 2. Status Webhook
- `BATCH_URL`: Endpoint for checking WAMID statuses (currently `https://webhook.rankachieversclasses.in/status/batch`).

### 3. Image Rendering (Playwright)
- **Environment:** `PLAYWRIGHT_BROWSERS_PATH` is set to `ms-playwright` in `Main.java`.
- **Initialization:** Playwright is initialized at startup.

### 4. Email Configuration
- **File:** `EmailService.java`
- **Requirement:** Requires valid SMTP credentials (Gmail App Password).

---

## Development Conventions

- **Threading:** Heavy operations (API calls, rendering) **must** run on a background thread to keep the JavaFX UI responsive. Use the `Task` pattern.
- **UI:** Prefer FXML and CSS (`rac-styles.css`). Sidebar navigation is managed via `ActivityService`.
- **Logging:** All major actions and errors must be logged using SLF4J.
- **Excel Handling:** Support both `Result` + `Contact` two-sheet format and single-sheet format.

---

## TODOs & Future Improvements
- [ ] Move API credentials and URLs to a secure local configuration file (`config.properties` or `.env`).
- [ ] Implement a "Settings" screen for dynamic configuration.
- [ ] Improve error handling for network-related failures in `WamidStatusService`.
- [ ] Add unit tests for `ExcelReaderService` and `WhatsAppApiService` logic.
