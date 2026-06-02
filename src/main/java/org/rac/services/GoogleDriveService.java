package org.rac.services;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Collections;

public class GoogleDriveService {
    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveService.class);
    private static final String APPLICATION_NAME = "RAC Operational App";
    private static final String CREDENTIALS_RESOURCE_PATH = "/credentials/google-credentials.json";
    private static final String EXTERNAL_CREDENTIALS_FILENAME = "google-credentials.json";

    private Drive getDriveService() throws IOException, GeneralSecurityException {
        InputStream in = null;
        
        // 1. Try to load from external file in current directory first (Override)
        File externalFile = new File(EXTERNAL_CREDENTIALS_FILENAME);
        if (externalFile.exists()) {
            logger.info("Loading Google credentials from external file: {}", externalFile.getAbsolutePath());
            in = new FileInputStream(externalFile);
        } else {
            // 2. Fallback to bundled resource
            logger.info("Loading Google credentials from bundled resource: {}", CREDENTIALS_RESOURCE_PATH);
            in = GoogleDriveService.class.getResourceAsStream(CREDENTIALS_RESOURCE_PATH);
        }

        if (in == null) {
            throw new IOException("Google Credentials file not found (tried external file '" + 
                    EXTERNAL_CREDENTIALS_FILENAME + "' and bundled resource '" + CREDENTIALS_RESOURCE_PATH + "')");
        }

        try (InputStream input = in) {
            // Fix: GitHub/Git might have converted \n to \r\n in the JSON file.
            // This corrupts the private key string and causes "Invalid JWT Signature".
            // We read the full content and force Unix-style line endings (\n) before parsing.
            byte[] rawBytes = input.readAllBytes();
            String content = new String(rawBytes, java.nio.charset.StandardCharsets.UTF_8);
            String sanitizedContent = content.replace("\r\n", "\n");
            
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(sanitizedContent.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .createScoped(Collections.singleton(DriveScopes.DRIVE_READONLY));

            return new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        }
    }

    public void downloadSpreadsheetAsExcel(String spreadsheetId, File targetFile) throws IOException, GeneralSecurityException {
        logger.info("Downloading Google Spreadsheet {} as Excel to {}", spreadsheetId, targetFile.getAbsolutePath());
        
        Drive service = getDriveService();
        
        // Export the Google Sheet as an .xlsx file
        try (OutputStream outputStream = new FileOutputStream(targetFile)) {
            service.files().export(spreadsheetId, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .executeMediaAndDownloadTo(outputStream);
            outputStream.flush();
        }
        
        logger.info("Successfully downloaded spreadsheet, size: {} bytes", targetFile.length());
    }
}
