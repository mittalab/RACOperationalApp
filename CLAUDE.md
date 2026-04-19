# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Run the app
mvn javafx:run

# Build fat JAR
mvn clean package

# Run the fat JAR
java -jar target/RACOperationalApp-1.0-SNAPSHOT.jar

# Package as Windows executable (after building)
jpackage --name RACOperationalApp --input target --main-jar RACOperationalApp-1.0-SNAPSHOT.jar --main-class org.rac.NewMain
```

There are no automated tests. Manual testing is the only approach; logs are written to `rac-operational-app.log`.

## Architecture

**JavaFX desktop app** (Java 25, JavaFX 25) following MVC with FXML views.

**Entry point:** `NewMain` → delegates to `Main` (extends `javafx.application.Application`), which loads `MainView.fxml`.

**Flow:**
1. `MainViewController` shows an activity selection screen (radio buttons).
2. Selecting an activity loads the activity's FXML — currently only `SendResultsView.fxml`.
3. `SendResultsViewController` handles the form, orchestrates services on a background thread, and updates a progress bar.

**Packages:**
- `gui/` — FXML controllers (event handlers, UI state)
- `model/` — `Student` (POJO), `Activity` (interface), `SendResultsActivity` (implementation)
- `services/` — all business logic
- `utils/` — `ImageUtils` (Base64 helper)

**Services and their roles:**
| Service | Role |
|---|---|
| `ExcelReaderService` | Reads `.xlsx` student data (Name, Phone, Marks, Details, Email) via Apache POI |
| `ResultImageService` | Fills `result_template.html` placeholders → renders to JPEG via Aspose HTML |
| `WhatsAppService` | Selenium-based WhatsApp Web automation — navigates to `web.whatsapp.com/send?phone=+91{phone}`, uploads image, sends |
| `EmailService` | SMTP via Gmail (JavaMail) with image attachment as fallback |
| `ExcelWriterService` | Generates abort report Excel when user stops a batch mid-run |
| `ActivityService` | Registry that maps activity names to their FXML paths |

## Key Configuration (Hardcoded — Must Be Updated Per Machine)

**`WhatsAppService.java` lines ~50-51:**
```java
System.setProperty("webdriver.chrome.driver", "C:/path/to/chromedriver.exe");
options.addArguments("user-data-dir=C:/path/to/Chrome/User Data/Profile");
```
Find the Chrome profile path at `chrome://version` → "Profile Path". ChromeDriver version must match installed Chrome.

**`EmailService.java`:** Gmail credentials (email + app-specific password) are hardcoded.

## HTML Result Template

`src/main/resources/result_template.html` — filled per student by `ResultImageService`. Placeholders replaced at render time: `NAME_INPUT`, `MARKS_INPUT`, `TOPIC_INPUT`, `DATE_INPUT`, `CLASS_INPUT`, `TOTAL_MARKS_INPUT`, `DETAILS_INPUT`.

Embedded images (`header.png`, `signature.png`, `watermark.png`) are Base64-encoded inline by `ImageUtils`.

## Adding a New Activity

1. Create a model class implementing `Activity` (returns a name and FXML resource path).
2. Create the FXML and its controller under `gui/`.
3. Register the activity in `ActivityService`.
