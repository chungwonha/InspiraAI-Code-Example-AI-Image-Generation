package com.chung.ai.software.inspiraai;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
//import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Component
public class YouTubeUploader {

    private static final String CLIENT_SECRETS= "path/to/client_secrets.json";
    private static final Collection<String> SCOPES = Collections.singletonList("https://www.googleapis.com/auth/youtube.upload");
    private static final String APPLICATION_NAME = "YouTubeUploader";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Value("${zapier.url}")
    private String zapierUrl;

//    public static void main(String[] args) throws GeneralSecurityException, IOException {
//        YouTube youtubeService = getService();
//        uploadVideo(youtubeService, "path/to/video.mp4", "Video Title", "Video Description", "22");
//    }

    public static YouTube getService() throws GeneralSecurityException, IOException {

        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new FileReader(CLIENT_SECRETS));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        return new YouTube.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public static void uploadVideo(YouTube youtubeService, String videoFilePath, String title, String description, String categoryId) throws IOException {
        Video videoObjectDefiningMetadata = new Video();

        VideoStatus status = new VideoStatus();
        status.setPrivacyStatus("public");
        videoObjectDefiningMetadata.setStatus(status);

        VideoSnippet snippet = new VideoSnippet();
        snippet.setTitle(title);
        snippet.setDescription(description);
        snippet.setCategoryId(categoryId);
        videoObjectDefiningMetadata.setSnippet(snippet);

        File mediaFile = new File(videoFilePath);
        FileContent mediaContent = new FileContent("video/*", mediaFile);
        ArrayList<String> partList = new ArrayList<>();
        partList.add("snippet,statistics,status");
        YouTube.Videos.Insert videoInsert = youtubeService.videos()
                .insert(partList, videoObjectDefiningMetadata, mediaContent);

        Video returnedVideo = videoInsert.execute();
        System.out.println("Video ID: " + returnedVideo.getId());
    }

    public void callZapierToUpload(String path) throws IOException {

        String jsonInputString = String.format("{\"path\":\"%s\"}", path);

        URL url = new URL(zapierUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; utf-8");
        connection.setDoOutput(true);

        try (java.io.OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        System.out.println("POST Response Code :: " + responseCode);

        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }

                System.out.println("Response: " + response.toString());
            }
        } else {
            System.out.println("POST request not worked");
        }
    }
}
