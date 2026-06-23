package org.rac;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.client.http.ByteArrayContent;
import org.rac.services.GoogleDriveService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

public class TestServiceAccountIntegration {
    private static final String MASTER_SPREADSHEET_ID = "16kElua-wgKkFJRkW8dzOPv9wzadYmKV9P_ydbr3BPFk";
    private static final String UPLOAD_ROOT_FOLDER_ID = "1U1WcDx8Ge5EcfNBxFDEQskmgM7MyxJNq";

    public static void main(String[] args) {
        System.out.println("=== Starting Service Account Integration Test ===");
        GoogleDriveService driveService = new GoogleDriveService();

        // Step 1: Download Contacts Spreadsheet
        java.io.File localExcelFile = new java.io.File("test_downloaded_contacts.xlsx");
        if (localExcelFile.exists()) {
            localExcelFile.delete();
        }

        try {
            System.out.println("Step 1: Attempting to download master spreadsheet: " + MASTER_SPREADSHEET_ID);
            driveService.downloadSpreadsheetAsExcel(MASTER_SPREADSHEET_ID, localExcelFile);
            System.out.println("SUCCESS: Downloaded spreadsheet! File size: " + localExcelFile.length() + " bytes");
        } catch (Exception e) {
            System.err.println("FAILED Step 1: Could not download spreadsheet.");
            e.printStackTrace();
            return;
        }

        // Step 2: Use Reflection or subclass to access private getDriveService() or construct it here
        // We will construct the client ourselves using the same logic to test write operations
        try {
            System.out.println("\nStep 2: Authenticating Google Drive client for folder & file creation...");
            
            // Re-use same service account login logic
            java.io.InputStream in = GoogleDriveService.class.getResourceAsStream("/credentials/google-credentials.json");
            if (in == null) {
                // Try current folder fallback
                java.io.File ext = new java.io.File("google-credentials.json");
                if (ext.exists()) {
                    in = new java.io.FileInputStream(ext);
                }
            }

            if (in == null) {
                throw new java.io.FileNotFoundException("google-credentials.json not found in resources or current directory!");
            }

            com.google.auth.oauth2.GoogleCredentials credentials = com.google.auth.oauth2.GoogleCredentials
                    .fromStream(in)
                    .createScoped(Collections.singleton(com.google.api.services.drive.DriveScopes.DRIVE));

            Drive service = new Drive.Builder(
                    com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport(),
                    com.google.api.client.json.gson.GsonFactory.getDefaultInstance(),
                    new com.google.auth.http.HttpCredentialsAdapter(credentials))
                    .setApplicationName("RAC Operational App Test")
                    .build();

            System.out.println("SUCCESS: Google Drive client initialized.");

            // Create a test folder
            System.out.println("\nStep 3: Creating test folder inside: " + UPLOAD_ROOT_FOLDER_ID);
            File folderMetadata = new File();
            folderMetadata.setName("Service Account Integration Test Folder");
            folderMetadata.setMimeType("application/vnd.google-apps.folder");
            folderMetadata.setParents(List.of(UPLOAD_ROOT_FOLDER_ID));

            File createdFolder = service.files().create(folderMetadata)
                    .setFields("id, name")
                    .execute();
            String createdFolderId = createdFolder.getId();
            System.out.println("SUCCESS: Created Folder: " + createdFolder.getName() + " (ID: " + createdFolderId + ")");

            // Create a test file in the folder
            System.out.println("\nStep 4: Uploading test text file inside created folder: " + createdFolderId);
            File fileMetadata = new File();
            fileMetadata.setName("integration_test_result.txt");
            fileMetadata.setParents(List.of(createdFolderId));

            String contentStr = "Service Account integration test was successful!\nGenerated at: " + java.time.LocalDateTime.now();
            ByteArrayContent content = new ByteArrayContent("text/plain", contentStr.getBytes(StandardCharsets.UTF_8));

            File uploadedFile = service.files().create(fileMetadata, content)
                    .setFields("id, name, webViewLink")
                    .execute();
            System.out.println("SUCCESS: Uploaded File: " + uploadedFile.getName() + " (ID: " + uploadedFile.getId() + ")");
            System.out.println("View Link: " + uploadedFile.getWebViewLink());

            System.out.println("\n=== Integration Test Finished Successfully! ===");

        } catch (Exception e) {
            System.err.println("FAILED Step 2/3/4 during write operations.");
            e.printStackTrace();
        }
    }
}
