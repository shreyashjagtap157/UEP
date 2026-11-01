package com.universalplatform.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "platform.storage.google-drive.enabled", havingValue = "true")
class GoogleDriveStorageAdapter implements StorageAdapter {
    private static final URI TOKEN_ENDPOINT = URI.create("https://oauth2.googleapis.com/token");

    private final HttpClient http;
    private final ObjectMapper json;
    private final GoogleDriveAccessTokenProvider tokens;
    private final GoogleDriveSharedDriveFolderValidator sharedDriveFolderValidator;
    private final String folderId;
    private final String sharedDriveId;

    @Autowired
    GoogleDriveStorageAdapter(
            @Value("${platform.storage.google-drive.access-token:}") String accessToken,
            @Value("${platform.storage.google-drive.client-id:}") String clientId,
            @Value("${platform.storage.google-drive.client-secret:}") String clientSecret,
            @Value("${platform.storage.google-drive.refresh-token:}") String refreshToken,
            @Value("${platform.storage.google-drive.folder-id:}") String folderId,
            @Value("${platform.storage.google-drive.shared-drive-id:}") String sharedDriveId,
            @Value("${platform.development-mode:false}") boolean developmentMode) {
        this(newHttpClient(), new ObjectMapper(), accessToken, clientId, clientSecret, refreshToken,
                folderId, sharedDriveId, developmentMode);
    }

    GoogleDriveStorageAdapter(
            HttpClient http, ObjectMapper json,
            String accessToken, String clientId, String clientSecret, String refreshToken,
            String folderId, String sharedDriveId, boolean developmentMode) {
        this.http = http;
        this.json = json;
        this.folderId = normalize(folderId);
        this.sharedDriveId = normalize(sharedDriveId);
        if (!this.sharedDriveId.isEmpty() && this.folderId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Google Shared Drive storage requires a folder ID inside the configured shared drive");
        }
        this.tokens = new GoogleDriveAccessTokenProvider(http, json, TOKEN_ENDPOINT, Clock.systemUTC(),
                accessToken, clientId, clientSecret, refreshToken, developmentMode);
        this.sharedDriveFolderValidator = new GoogleDriveSharedDriveFolderValidator(http, json);
    }

    private static HttpClient newHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public StorageProviderType type() {
        return sharedDriveId.isEmpty()
                ? StorageProviderType.GOOGLE_DRIVE
                : StorageProviderType.GOOGLE_SHARED_DRIVE;
    }

    @Override
    public String put(String key, InputStream source, long length, String contentType) throws IOException {
        try {
            if (!sharedDriveId.isEmpty()) {
                sharedDriveFolderValidator.validate(folderId, sharedDriveId, tokens.accessToken());
            }
            String metadata = "{\"name\":\"" + escape(key) + "\""
                    + (folderId.isEmpty() ? "" : ",\"parents\":[\"" + escape(folderId) + "\"]") + "}";
            String boundary = "uep-boundary";
            byte[] head = ("--" + boundary
                    + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n"
                    + metadata + "\r\n--" + boundary
                    + "\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.concat(
                    HttpRequest.BodyPublishers.ofByteArray(head),
                    HttpRequest.BodyPublishers.ofInputStream(() -> source),
                    HttpRequest.BodyPublishers.ofByteArray(tail));
            String uri = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&supportsAllDrives=true";
            HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                    .timeout(Duration.ofMinutes(30))
                    .header("Authorization", "Bearer " + tokens.accessToken())
                    .header("Content-Type", "multipart/related; boundary=" + boundary)
                    .POST(body)
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("Google Drive upload failed: HTTP " + response.statusCode());
            }
            JsonNode result = json.readTree(response.body());
            String id = result.path("id").asText("");
            if (id.isBlank()) throw new IOException("Google Drive upload response did not contain a file ID");
            return id;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Google Drive upload interrupted", interrupted);
        }
    }

    @Override
    public InputStream open(String id) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://www.googleapis.com/drive/v3/files/" + id
                                    + "?alt=media&supportsAllDrives=true"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + tokens.accessToken())
                    .GET()
                    .build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                response.body().close();
                throw new IOException("Google Drive download failed: HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Google Drive download interrupted", interrupted);
        }
    }

    @Override
    public void delete(String id) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://www.googleapis.com/drive/v3/files/" + id + "?supportsAllDrives=true"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + tokens.accessToken())
                    .DELETE()
                    .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2 && response.statusCode() != 404) {
                throw new IOException("Google Drive delete failed: HTTP " + response.statusCode());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Google Drive delete interrupted", interrupted);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
