package com.universalplatform.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GoogleDriveAccessTokenProviderTest {
    private static final URI TOKEN_ENDPOINT = URI.create("https://oauth2.example.test/token");

    @Test
    void refreshesAndCachesAccessTokenForItsUsableLifetime() throws Exception {
        HttpClient http = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"access_token\":\"renewed-token\",\"expires_in\":3600}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        var provider = provider(http, "", "client-id", "client-secret", "refresh-token", false);

        assertThat(provider.accessToken()).isEqualTo("renewed-token");
        assertThat(provider.accessToken()).isEqualTo("renewed-token");
        verify(http, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void rejectsNonSuccessfulRefreshWithoutLeakingResponseBody() throws Exception {
        HttpClient http = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn("invalid_grant refresh-token-secret");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        var provider = provider(http, "", "client-id", "client-secret", "refresh-token-secret", false);

        assertThatThrownBy(provider::accessToken)
                .isInstanceOf(IOException.class)
                .hasMessage("Google OAuth token refresh failed: HTTP 400")
                .hasMessageNotContaining("refresh-token-secret");
    }

    @Test
    void staticAccessTokenIsAllowedOnlyInDevelopmentMode() throws Exception {
        var provider = provider(Mockito.mock(HttpClient.class), "dev-access-token", "", "", "", true);

        assertThat(provider.accessToken()).isEqualTo("dev-access-token");
    }

    @Test
    void rejectsStaticAccessTokenInProduction() {
        assertThatThrownBy(() -> provider(Mockito.mock(HttpClient.class),
                "short-lived-token", "", "", "", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("production storage requires OAuth refresh credentials");
    }

    @Test
    void rejectsPartialRefreshCredentials() {
        assertThatThrownBy(() -> provider(Mockito.mock(HttpClient.class),
                "", "client-id", "", "refresh-token", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must all be configured together");
    }

    @Test
    void rejectsRefreshResponsesWithoutAValidLifetime() throws Exception {
        HttpClient http = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"access_token\":\"renewed-token\",\"expires_in\":0}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        var provider = provider(http, "", "client-id", "client-secret", "refresh-token", false);

        assertThatThrownBy(provider::accessToken)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("valid access token lifetime");
    }

    private static GoogleDriveAccessTokenProvider provider(
            HttpClient http, String accessToken, String clientId, String clientSecret,
            String refreshToken, boolean developmentMode) {
        return new GoogleDriveAccessTokenProvider(http, new ObjectMapper(), TOKEN_ENDPOINT, Clock.systemUTC(),
                accessToken, clientId, clientSecret, refreshToken, developmentMode);
    }
}
