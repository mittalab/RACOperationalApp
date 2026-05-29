package org.rac.services;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;

public class GoogleDriveService {
    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveService.class);
    private static final String APPLICATION_NAME = "RAC Operational App";
    private static final String CREDENTIALS_FILE_PATH = "google-credentials.json";

    private Drive getDriveService() throws IOException, GeneralSecurityException {
        GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(CREDENTIALS_FILE_PATH))
                .createScoped(Collections.singleton(DriveScopes.DRIVE_READONLY));

        return new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public void downloadSpreadsheetAsExcel(String spreadsheetId, File targetFile) throws IOException, GeneralSecurityException {
        logger.info("Downloading Google Spreadsheet {} as Excel to {}", spreadsheetId, targetFile.getAbsolutePath());
        
        Drive service = getDriveService();
        
        // Export the Google Sheet as an .xlsx file
        OutputStream outputStream = new FileOutputStream(targetFile);
        service.files().export(spreadsheetId, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .executeMediaAndDownloadTo(outputStream);
        
        outputStream.flush();
        outputStream.close();
        
        logger.info("Successfully downloaded spreadsheet to {}", targetFile.length());
    }
}
