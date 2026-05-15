# RAC Operational App

A JavaFX desktop application that automates operational tasks for Rank Achievers Classes — primarily sending student test results to parents via WhatsApp and tracking delivery.

---

## Features

| Activity | Description |
|---|---|
| **Send Results to Parents** | Generates per-student result images from an Excel sheet, uploads them to the WhatsApp Cloud API, and sends a templated message to each parent. Also notifies the admin (Nupur Madam) with a topper-list image and a summary. Saves a `run_report.xlsx` to the output folder. |
| **Check WhatsApp Status** | Manually look up the delivery status of one or more WAMIDs (comma-separated). Shows status, timestamps, and error details in a table with a live summary bar. |

---

## Architecture

**Stack:** Java 25 · JavaFX 25 · Maven · MVC with FXML views

### Entry Point

```
NewMain → Main (extends Application) → MainView.fxml
```

### Navigation

The `MainView` has a persistent left sidebar with clickable nav items. Clicking an activity item navigates to its full-screen view. A "← Back to Home" button in each activity view returns to `MainView`.

### Packages

```
org.rac
├── gui/          FXML controllers
├── model/        POJOs and Activity registrations
├── services/     All business logic
└── utils/        Helpers (ImageUtils)
```

### Controllers

| Controller | Responsibility |
|---|---|
| `MainViewController` | Sidebar nav — dynamically lists all registered activities |
| `SendResultsViewController` | Orchestrates the full send-results pipeline on a background thread |
| `DeliveryTrackerViewController` | Non-modal window; batches all WAMIDs in one API call on open and on refresh; shows per-status summary bar |
| `CheckWamidStatusViewController` | Manual WAMID lookup; calls batch status API; shows result table with summary bar |
| `CompletionSummaryViewController` | Post-run summary dialog with "Track Delivery" button |
| `ConfirmationViewController` | Pre-run confirmation dialog |
| `CutOffViewController` | Topper cut-off selection dialog |

### Services

| Service | Role |
|---|---|
| `WhatsAppApiService` | WhatsApp Cloud API — uploads media, sends student/topper/summary templates, handles retries and quota errors |
| `WamidStatusService` | Batch status lookup via `POST https://webhook.rankachieversclasses.in/status/batch` — chunks up to 100 WAMIDs per request, parses all status fields (timestamps, error codes) |
| `ResultImageService` | Fills `result_template_4.html` placeholders → renders to PNG via Microsoft Playwright (headless Chromium) |
| `ExcelReaderService` | Reads `.xlsx` student data (Name, Phone, Marks, Details) via Apache POI |
| `ExcelWriterService` | (1) Abort report — students processed before abort; (2) Run report — Name · Phone · Image file · WAMID for every student, plus two ADMIN rows |
| `EmailService` | SMTP fallback via Gmail (JavaMail) |
| `ActivityService` | Registry — returns the ordered list of `Activity` implementations shown in the sidebar |

### Models

| Model | Description |
|---|---|
| `Student` | Immutable POJO: name, phone, marksObtained, additionalDetails, rollNo |
| `MessageDelivery` | JavaFX property model for the delivery tracker: studentName, phone, messageId (WAMID), status, lastChecked |
| `RunRecord` | Java record: studentName, phone, imageName, wamid — used for the run-report Excel |
| `Activity` | Interface: `getName()`, `getFxmlPath()` |
| `SendResultsActivity` | Registers "Send Results to Parents" |
| `CheckWamidStatusActivity` | Registers "Check WhatsApp Status" |

### Send Results Pipeline

```
Excel file
  └─► ExcelReaderService.readAndValidate()
        └─► [per student]
              ├─ ResultImageService.generateImage()   → PNG
              ├─ WhatsAppApiService.uploadMedia()     → mediaId
              ├─ WhatsAppApiService.sendStudentResult() → wamid
              └─ RunRecord collected
        └─► [admin]
              ├─ ResultImageService.generateTopperImage() → Toppers_List.png
              ├─ WhatsAppApiService.sendTopperResult()   → topperWamid  (→ deliveryRecords)
              └─ WhatsAppApiService.sendResultSummary()  → summaryWamid (→ deliveryRecords)
        └─► ExcelWriterService.writeRunReport()  → run_report.xlsx  (in PNG output folder)
        └─► CompletionSummaryViewController
              └─► [optional] DeliveryTrackerViewController
```

### Status Tracking Flow

```
DeliveryTrackerViewController.setup()
  └─► handleRefresh()  (fires immediately on open, again every 30 s if auto-refresh enabled)
        └─► WamidStatusService.checkBatch(allWamids)
              └─► POST webhook.rankachieversclasses.in/status/batch
                    └─► update each MessageDelivery.status + lastChecked
                          └─► updateSummary()  (Total / Sent / Delivered / Read / Failed / Pending)
```

### Webhook Service Endpoints

All WAMID status data is served by the RAC webhook server:

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/webhook` | Meta webhook verification |
| `POST` | `/webhook` | Receive live status updates from Meta |
| `GET` | `/status?wamid=<id>` | Single message status |
| `POST` | `/status/batch` | Batch message status (up to 100 WAMIDs) |
| `GET` | `/status/all` | All current entries |
| `POST` | `/dump` | Dump Redis to Google Drive |

---

## Configuration (Per-Machine / Per-Deployment)

All secrets are currently hardcoded. Update these before running:

### `WhatsAppApiService.java`

```java
private static final String PHONE_ID     = "<your Meta phone number ID>";
private static final String BEARER_TOKEN = "<your WhatsApp Cloud API bearer token>";
private static final String ADMIN_PHONE  = "<admin WhatsApp number with country code, no +>";
```

Get `PHONE_ID` and `BEARER_TOKEN` from [Meta for Developers → WhatsApp → API Setup](https://developers.facebook.com/apps/).  
Bearer tokens expire — regenerate from the Meta dashboard.

### `EmailService.java`

```java
// Gmail address and app-specific password (not your login password)
```

Generate an app password at [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords).

### `WamidStatusService.java`

```java
private static final String BATCH_URL = "https://webhook.rankachieversclasses.in/status/batch";
```

Update if the webhook server hostname changes.

---

## HTML Result Templates

Located in `src/main/resources/`:

| Template | Used for |
|---|---|
| `result_template_4.html` | Individual student result card |
| `topper_template.html` | Topper-list image sent to admin |

**Placeholders** replaced at render time: `NAME_INPUT`, `MARKS_INPUT`, `TOPIC_INPUT`, `DATE_INPUT`, `CLASS_INPUT`, `TOTAL_MARKS_INPUT`, `DETAILS_INPUT`, `HEADING_INPUT`, `ADDITIONAL_INPUT`.

Images (`header.png`, `signature.png`, `watermark.png`) are Base64-encoded inline by `ImageUtils`.

---

## Excel Input Format

The input `.xlsx` file must have these columns (order matters):

| Column | Content |
|---|---|
| A | Student name |
| B | Phone number (10 digits, country code added automatically) |
| C | Marks obtained |
| D | Additional details |

**Filename convention** for auto-population of Class and Batch fields:
```
file_<CLASS>_<batch-token>_student_data.xlsx
```
Example: `file_10_tuesday_6_7_student_data.xlsx`

---

## Output Files

After each run, a UUID-named folder is created in the project root directory:

```
<uuid>_png/
  ├── result_<StudentName>_0.png   ← per-student result images
  ├── result_<StudentName>_1.png
  ├── ...
  ├── Toppers_List.png             ← topper image sent to admin
  └── run_report.xlsx              ← Name · Phone · Image File · WAMID for all students
                                      + 2 ADMIN rows (Topper List, Summary)
```

---

## Build & Run

### Prerequisites

- Java 25 (JDK 25)
- Maven 3.8+
- Internet access (WhatsApp Cloud API + webhook server)

No ChromeDriver or Chrome installation required — image rendering uses Microsoft Playwright's bundled Chromium.

### Commands

```bash
# Run in development
mvn javafx:run

# Build fat JAR
mvn clean package

# Run the JAR
java -jar target/RACOperationalApp-1.0-SNAPSHOT.jar

# Package as Windows executable
jpackage --name RACOperationalApp \
         --input target \
         --main-jar RACOperationalApp-1.0-SNAPSHOT.jar \
         --main-class org.rac.NewMain
```

### Logs

All logs are written to `rac-operational-app.log` in the project root (also echoed to console). Useful for debugging API calls and image generation errors.

---

## Adding a New Activity

1. Create a class implementing `Activity`:
   ```java
   public class MyActivity implements Activity {
       public String getName()     { return "My Activity"; }
       public String getFxmlPath() { return "/org/rac/gui/MyActivityView.fxml"; }
   }
   ```
2. Create `MyActivityView.fxml` and its controller under `gui/`.
3. Add `new MyActivity()` to the list in `ActivityService.getActivities()`.
4. The sidebar picks it up automatically — no further wiring needed.
