package org.rac;

import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.DriveScopes;

import java.io.*;
import java.util.Collections;

public class TokenExchangeUtility {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: TokenExchangeUtility <auth-code> [redirect-uri]");
            System.exit(1);
        }
        String authCode = args[0];
        com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver receiver = new com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver.Builder().setPort(8888).build();
        String redirectUri = receiver.getRedirectUri();
        System.out.println("Receiver redirect URI: " + redirectUri);
        if (args.length > 1) {
            redirectUri = args[1];
        }

        System.out.println("Using auth code: " + authCode);
        System.out.println("Using redirect URI: " + redirectUri);

        // Load client secrets
        File externalFile = new File("client_secrets.json");
        InputStream in;
        if (externalFile.exists()) {
            System.out.println("Loading external client_secrets.json...");
            in = new FileInputStream(externalFile);
        } else {
            System.out.println("Loading bundled client_secrets.json...");
            in = TokenExchangeUtility.class.getResourceAsStream("/credentials/client_secrets.json");
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

        try {
            // Exchange code for token
            GoogleTokenResponse tokenResponse = flow.newTokenRequest(authCode)
                    .setRedirectUri(redirectUri)
                    .execute();

            System.out.println("Token request successful!");
            System.out.println("Access Token: " + tokenResponse.getAccessToken());
            System.out.println("Refresh Token: " + tokenResponse.getRefreshToken());
            System.out.println("Expires In: " + tokenResponse.getExpiresInSeconds());

            // Save credential
            flow.createAndStoreCredential(tokenResponse, "user");
            System.out.println("Successfully stored credential to 'tokens' directory!");
        } catch (TokenResponseException e) {
            System.err.println("Error responding to token: " + e.getDetails());
            e.printStackTrace();
        }
    }
}
