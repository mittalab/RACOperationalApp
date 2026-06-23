package org.rac;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.DriveScopes;

import java.io.*;
import java.util.Collections;

public class ValidateStoredToken {
    public static void main(String[] args) throws Exception {
        System.out.println("Loading client secrets...");
        File externalFile = new File("client_secrets.json");
        InputStream in;
        if (externalFile.exists()) {
            in = new FileInputStream(externalFile);
        } else {
            in = ValidateStoredToken.class.getResourceAsStream("/credentials/client_secrets.json");
        }

        if (in == null) {
            throw new FileNotFoundException("client_secrets.json not found!");
        }

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                GsonFactory.getDefaultInstance(), new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                clientSecrets,
                Collections.singleton(DriveScopes.DRIVE))
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File("tokens")))
                .setAccessType("offline")
                .build();

        System.out.println("Loading stored credential for user 'user'...");
        Credential credential = flow.loadCredential("user");

        if (credential == null) {
            System.out.println("No stored credential found for 'user' in the data store!");
            return;
        }

        System.out.println("Stored Credential Details:");
        System.out.println("  Access Token: " + credential.getAccessToken());
        System.out.println("  Refresh Token: " + credential.getRefreshToken());
        System.out.println("  Expires In Seconds: " + credential.getExpiresInSeconds());

        try {
            System.out.println("Attempting to refresh access token...");
            boolean refreshed = credential.refreshToken();
            if (refreshed) {
                System.out.println("Token refresh successful!");
                System.out.println("  New Access Token: " + credential.getAccessToken());
                System.out.println("  Expires In Seconds: " + credential.getExpiresInSeconds());
            } else {
                System.out.println("Token refresh call returned false (no refresh token or could not refresh).");
            }
        } catch (TokenResponseException e) {
            System.err.println("TokenResponseException occurred during refresh!");
            System.err.println("Error details: " + e.getDetails());
            System.err.println("Status Code: " + e.getStatusCode());
            System.err.println("Message: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("IOException occurred during refresh!");
            e.printStackTrace();
        }
    }
}
