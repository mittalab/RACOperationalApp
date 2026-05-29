package org.rac.services;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Temporary utility to verify Google Sheets connection and list sheet names.
 */
public class GoogleVerificationService {
    private static final Logger logger = LoggerFactory.getLogger(GoogleVerificationService.class);
    private static final String APPLICATION_NAME = "RAC Operational App";
    private static final String CREDENTIALS_FILE_PATH = "google-credentials.json";

    public List<String> getSheetNames(String spreadsheetId) throws IOException, GeneralSecurityException {
        logger.info("Connecting to Google Sheets for spreadsheet: {}", spreadsheetId);
        
        GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(CREDENTIALS_FILE_PATH))
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS_READONLY));

        Sheets service = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
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
