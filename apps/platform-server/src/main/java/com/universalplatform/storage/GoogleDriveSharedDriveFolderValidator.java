package com.universalplatform.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Verifies that a configured destination is a folder in the expected Shared Drive.
 * A non-empty folder ID alone is insufficient: Drive otherwise accepts a My Drive
 * folder and silently violates the tenant's intended storage destination.
 */
final class GoogleDriveSharedDriveFolderValidator {
    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
    private final HttpClient http;
    private final ObjectMapper json;

    GoogleDriveSharedDriveFolderValidator(HttpClient http, ObjectMapper json) {
        this.http = http;
        this.json = json;
    }

    void validate(String folderId, String sharedDriveId, String accessToken) throws IOException {
        if (folderId == null || folderId.isBlank() || sharedDriveId == null || sharedDriveId.isBlank()) {
            throw new IllegalArgumentException("Shared Drive validation requires both a folder ID and a drive ID");
        }
        String encodedFolderId = URLEncoder.encode(folderId, StandardCharsets.UTF_8).replace("+", "%20");
        URI uri = URI.create("https://www.googleapis.com/drive/v3/files/" + encodedFolderId
                + "?fields=id%2CmimeType%2CdriveId&supportsAllDrives=true");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        final HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Google Shared Drive folder validation was interrupted", interrupted);
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Google Shared Drive folder validation failed: HTTP " + response.statusCode());
        }

        JsonNode folder = json.readTree(response.body());
        if (!folderId.equals(folder.path("id").asText())
                || !FOLDER_MIME_TYPE.equals(folder.path("mimeType").asText())
                || !sharedDriveId.equals(folder.path("driveId").asText())) {
            throw new IOException("Configured Google Drive folder is not a folder inside the configured Shared Drive");
        }
    }
}
