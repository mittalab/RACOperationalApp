package org.rac.services;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public class GoogleDriveService {
    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveService.class);
    private static final String APPLICATION_NAME = "RAC Operational App";
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String CLIENT_SECRETS_RESOURCE_PATH = "/credentials/client_secrets.json";
    private static final String EXTERNAL_CLIENT_SECRETS_FILENAME = "client_secrets.json";
    private static final String UPLOAD_ROOT_FOLDER_ID = "1U1WcDx8Ge5EcfNBxFDEQskmgM7MyxJNq";

    private Drive getDriveService() throws IOException, GeneralSecurityException {
        InputStream in = null;
        File externalFile = new File(EXTERNAL_CLIENT_SECRETS_FILENAME);
        if (externalFile.exists()) {
            logger.info("Loading Google Client Secrets from external file: {}", externalFile.getAbsolutePath());
            in = new FileInputStream(externalFile);
        } else {
            logger.info("Loading Google Client Secrets from bundled resource: {}", CLIENT_SECRETS_RESOURCE_PATH);
            in = GoogleDriveService.class.getResourceAsStream(CLIENT_SECRETS_RESOURCE_PATH);
        }

        if (in == null) {
            throw new IOException("Google Client Secrets file not found (tried external file '" + 
                    EXTERNAL_CLIENT_SECRETS_FILENAME + "' and bundled resource '" + CLIENT_SECRETS_RESOURCE_PATH + "'). " +
                    "Please provide client_secrets.json for OAuth 2.0.");
        }

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                GsonFactory.getDefaultInstance(), new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                clientSecrets,
                Collections.singleton(DriveScopes.DRIVE))
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(-1).build();
        Credential credential;
        try {
            credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        } catch (com.google.api.client.auth.oauth2.TokenResponseException e) {
            if ("invalid_grant".equals(e.getDetails() != null ? e.getDetails().getError() : null) ||
                (e.getMessage() != null && e.getMessage().contains("invalid_grant"))) {
                logger.warn("Stored Google credentials are expired or revoked. Clearing token store and retrying authorization...");
                flow.getCredentialDataStore().clear();
                credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
            } else {
                throw e;
            }
        }

        return new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
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

    private String findOrCreateFolder(Drive service, String name, String parentId) throws IOException {
        FileList result = service.files().list()
                .setQ("mimeType='application/vnd.google-apps.folder' and name='" + name +
                      "' and '" + parentId + "' in parents and trashed=false")
                .setFields("files(id)").execute();
        if (!result.getFiles().isEmpty()) return result.getFiles().get(0).getId();
        com.google.api.services.drive.model.File meta = new com.google.api.services.drive.model.File();
        meta.setName(name);
        meta.setMimeType("application/vnd.google-apps.folder");
        meta.setParents(List.of(parentId));
        return service.files().create(meta).setFields("id").execute().getId();
    }

    private void uploadFile(Drive service, File localFile, String folderId) throws IOException {
        com.google.api.services.drive.model.File meta = new com.google.api.services.drive.model.File();
        meta.setName(localFile.getName());
        meta.setParents(List.of(folderId));
        String mimeType = Files.probeContentType(localFile.toPath());
        FileContent content = new FileContent(mimeType != null ? mimeType : "application/octet-stream", localFile);
        service.files().create(meta, content).setFields("id").execute();
    }

    public void uploadRunFolder(File pngDir, LocalDate date, String userName) throws IOException, GeneralSecurityException {
        logger.info("Uploading run folder {} to Drive as {}/{}", pngDir.getName(), date, userName);
        Drive service = getDriveService();
        String yearId  = findOrCreateFolder(service, String.valueOf(date.getYear()), UPLOAD_ROOT_FOLDER_ID);
        String monthId = findOrCreateFolder(service, String.format("%02d", date.getMonthValue()), yearId);
        String dayId   = findOrCreateFolder(service, String.format("%02d", date.getDayOfMonth()), monthId);
        String userFolderId = findOrCreateFolder(service, userName, dayId);
        String runFolderId  = findOrCreateFolder(service, pngDir.getName(), userFolderId);
        logger.info("Drive run folder ID: {}", runFolderId);
        File[] files = pngDir.listFiles(File::isFile);
        if (files != null) {
            for (File f : files) {
                logger.info("Uploading: {}", f.getName());
                uploadFile(service, f, runFolderId);
            }
        }
        logger.info("Drive upload complete: {} files", files != null ? files.length : 0);
    }
}
