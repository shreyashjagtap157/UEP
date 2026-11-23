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
import java.time.Clock;
import java.time.Instant;

/**
 * Supplies renewable Google OAuth access tokens for long-running server-side Drive access.
 * Static access tokens are deliberately restricted to development mode.
 */
final class GoogleDriveAccessTokenProvider {
    private final HttpClient http;
    private final ObjectMapper json;
    private final URI tokenEndpoint;
    private final Clock clock;
    private final String staticAccessToken;
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final boolean allowStaticToken;
    private volatile CachedToken cachedToken;

    GoogleDriveAccessTokenProvider(HttpClient http, ObjectMapper json, URI tokenEndpoint, Clock clock,
                                   String staticAccessToken, String clientId, String clientSecret,
                                   String refreshToken, boolean developmentMode) {
        this.http = http;
        this.json = json;
        this.tokenEndpoint = tokenEndpoint;
        this.clock = clock;
        this.staticAccessToken = normalize(staticAccessToken);
        this.clientId = normalize(clientId);
        this.clientSecret = normalize(clientSecret);
        this.refreshToken = normalize(refreshToken);
        this.allowStaticToken = developmentMode;

        boolean hasClientId = !this.clientId.isEmpty();
        boolean hasClientSecret = !this.clientSecret.isEmpty();
        boolean hasRefreshToken = !this.refreshToken.isEmpty();
        boolean anyRefreshCredential = hasClientId || hasClientSecret || hasRefreshToken;
        boolean allRefreshCredentials = hasClientId && hasClientSecret && hasRefreshToken;
        if (anyRefreshCredential && !allRefreshCredentials) {
            throw new IllegalArgumentException(
                    "Google Drive OAuth client ID, client secret, and refresh token must all be configured together");
        }
        if (!allRefreshCredentials && !(developmentMode && !this.staticAccessToken.isEmpty())) {
            throw new IllegalArgumentException(
                    "Google Drive production storage requires OAuth refresh credentials; static access tokens are development-only");
        }
    }

    String accessToken() throws IOException {
        if (clientId.isEmpty()) return staticAccessToken;

        CachedToken current = cachedToken;
        if (current != null && clock.instant().isBefore(current.refreshAt())) {
            return current.value();
        }

        synchronized (this) {
            current = cachedToken;
            if (current != null && clock.instant().isBefore(current.refreshAt())) {
                return current.value();
            }
            CachedToken refreshed = refresh();
            cachedToken = refreshed;
            return refreshed.value();
        }
    }

    private CachedToken refresh() throws IOException {
        String form = "grant_type=refresh_token"
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&refresh_token=" + encode(refreshToken);
        HttpRequest request = HttpRequest.newBuilder(tokenEndpoint)
                .timeout(java.time.Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();

        final HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Google OAuth token refresh was interrupted", interrupted);
        }
        if (response.statusCode() != 200) {
            throw new IOException("Google OAuth token refresh failed: HTTP " + response.statusCode());
        }

        JsonNode body = json.readTree(response.body());
        String value = body.path("access_token").asText("");
        long expiresIn = body.path("expires_in").asLong(0);
        if (value.isBlank() || expiresIn <= 0) {
            throw new IOException("Google OAuth token response did not contain a valid access token lifetime");
        }
        long refreshSkew = Math.min(60, Math.max(1, expiresIn / 10));
        Instant refreshAt = clock.instant().plusSeconds(Math.max(0, expiresIn - refreshSkew));
        return new CachedToken(value, refreshAt);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private record CachedToken(String value, Instant refreshAt) {}
}
