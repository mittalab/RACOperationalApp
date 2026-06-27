# Implementation Plan - Google Drive Uploads, Name Matching Trimming, and Mismatch Summary Dropdown

This plan outlines the changes required to solve the three issues mentioned in the user request:
1. Prevent uploading temporary files (e.g. `~$cloud_contacts.xlsx`) to Google Drive.
2. Remove leading/trailing spaces (and handle Unicode spaces) when matching names from results and contact files.
3. Show a count and two collapsible dropdowns (similar to confirmation summary) when contact names mismatch: one for mismatched names, and one for all available contacts with phone numbers.
4. Provide a button to open the Results Excel file (the file with the student marks) so the user can easily edit and correct the mismatched student names.

## Proposed Changes

### Core services & logic

#### [MODIFY] [GoogleDriveService.java](file:///C:/Users/29abh/Projects/RAC_Projects/RACOperationalApp/src/main/java/org/rac/services/GoogleDriveService.java)
- In the `uploadRunFolder` method, filter the files inside `pngDir` to exclude temporary files and OS metadata.
- Specifically, ignore files that:
  - Are hidden (`f.isHidden()`) - covers system files like `Thumbs.db`.
  - Start with `~$` (standard MS Office temp/lock files).
  - Start with `.` (hidden dotfiles).
  - End with `.tmp` or `.temp` (explicit temporary files).

#### [MODIFY] [ExcelReaderService.java](file:///C:/Users/29abh/Projects/RAC_Projects/RACOperationalApp/src/main/java/org/rac/services/ExcelReaderService.java)
- Define a public static inner class `StudentContact` containing student name and phone.
- Add `mismatchedNames` and `allContacts` lists to `ExcelReadResult`.
- Update `normalizeName` to handle Unicode whitespaces (like `\u00A0` non-breaking space) and strip them.
- Populate `allContacts` while reading contacts from sheet.
- Populate `mismatchedNames` when names cannot be matched (or resolved) during parsing.

#### [MODIFY] [Student.java](file:///C:/Users/29abh/Projects/RAC_Projects/RACOperationalApp/src/main/java/org/rac/model/Student.java)
- Update the constructor to use `ExcelReaderService.normalizeName(name)` to ensure name normalization is consistent across the application.

---

### UI Components & Controllers

#### [NEW] [MismatchSummaryView.fxml](file:///C:/Users/29abh/Projects/RAC_Projects/RACOperationalApp/src/main/resources/org/rac/gui/MismatchSummaryView.fxml)
- A new FXML layout file based on the design of `ConfirmationView.fxml` and `MessageDialogView.fxml`.
- Features a header with an error icon, a mismatch summary card, a collapsible dropdown/TitledPane for mismatched names (using `TableView`), and another collapsible dropdown/TitledPane for available contacts (using `TableView` with Name and Phone number columns).
- Buttons to "Close" and "Open Results File" (to easily edit the Results Excel sheet).

#### [NEW] [MismatchSummaryViewController.java](file:///C:/Users/29abh/Projects/RAC_Projects/RACOperationalApp/src/main/java/org/rac/gui/MismatchSummaryViewController.java)
- The controller for the new `MismatchSummaryView`.
- Configures tables and handles the "Open Results File" action on a background thread.

#### [MODIFY] [SendResultsViewController.java](file:///C:/Users/29abh/Projects/RAC_Projects/RACOperationalApp/src/main/java/org/rac/gui/SendResultsViewController.java)
- In the validation section, check if there are mismatched names in the validation results. If so, show the new `MismatchSummaryView` passing the `excelFile` (Results sheet) instead of a plain warning alert.

#### [MODIFY] [SendAdminNotificationsViewController.java](file:///C:/Users/29abh/Projects/RAC_Projects/RACOperationalApp/src/main/java/org/rac/gui/SendAdminNotificationsViewController.java)
- Implement the same mismatch dialog invocation in the validation logic, passing the `excelFile`.

## Verification Plan

### Automated Tests / Compile check
- Verify compile and package succeed using:
  ```bash
  mvn clean package
  ```

### Manual Verification
1. Open Excel sheet, run results delivery, verify that temporary `~$` files and other temporary files are not uploaded to Google Drive.
2. Introduce spaces (both regular and non-breaking `\u00A0`) at the beginning and end of some names in result/contact files and verify they still match successfully.
3. Intentionally mismatch a student name in the results file (e.g. change "Amit" to "Amit Test"), run validation, and verify the mismatch summary dialog appears showing:
   - Mismatched name count.
   - Collapsible panel showing "Amit Test" as mismatched.
   - Collapsible panel showing all contacts with their names and phone numbers.
   - Clicking "Open Results File" successfully opens the local results Excel file.
