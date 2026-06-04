package org.rac.services;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility to verify Google Sheets connection and list sheet names using OAuth 2.0.
 */
public class GoogleVerificationService {
    private static final Logger logger = LoggerFactory.getLogger(GoogleVerificationService.class);
    private static final String APPLICATION_NAME = "RAC Operational App";
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final String CLIENT_SECRETS_RESOURCE_PATH = "/credentials/client_secrets.json";
    private static final String EXTERNAL_CLIENT_SECRETS_FILENAME = "client_secrets.json";

    private Credential getCredentials() throws IOException, GeneralSecurityException {
        InputStream in = null;
        File externalFile = new File(EXTERNAL_CLIENT_SECRETS_FILENAME);
        if (externalFile.exists()) {
            in = new FileInputStream(externalFile);
        } else {
            in = GoogleVerificationService.class.getResourceAsStream(CLIENT_SECRETS_RESOURCE_PATH);
        }

        if (in == null) {
            throw new IOException("Google Client Secrets file not found.");
        }

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                GsonFactory.getDefaultInstance(), new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                clientSecrets,
                Collections.singleton(SheetsScopes.SPREADSHEETS_READONLY))
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    public List<String> getSheetNames(String spreadsheetId) throws IOException, GeneralSecurityException {
        logger.info("Connecting to Google Sheets for spreadsheet: {}", spreadsheetId);
        
        Sheets service = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                getCredentials())
                .setApplicationName(APPLICATION_NAME)
                .build();

        Spreadsheet spreadsheet = service.spreadsheets().get(spreadsheetId).execute();
        List<Sheet> sheets = spreadsheet.getSheets();

        List<String> sheetNames = sheets.stream()
                .map(s -> s.getProperties().getTitle())
                .collect(Collectors.toList());
        
        logger.info("Successfully retrieved {} sheet names", sheetNames.size());
        return sheetNames;
    }

    public static void main(String[] args) {
        String spreadsheetId = "16kElua-wgKkFJRkW8dzOPv9wzadYmKV9P_ydbr3BPFk";
        //String spreadsheetId = "1WzK6Le_v9jPXcKous50Pjs3smnhC3tWIoDiyhmUXulI";
        try {
            GoogleVerificationService service = new GoogleVerificationService();
            List<String> names = service.getSheetNames(spreadsheetId);
            System.out.println("--- SHEET NAMES ---");
            for (String name : names) {
                System.out.println("- " + name);
            }
            System.out.println("-------------------");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
