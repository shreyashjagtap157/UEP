package com.universalplatform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import org.junit.jupiter.api.Test;

class WebhookWorkerFailureRedactionTest {
    @Test
    void transportErrorsDoNotPersistCallbackUrlOrQueryCredentials() {
        Exception failure = new IOException(
                "connection failed for https://hooks.example.com/events?access_token=secret-value");

        String safe = WebhookWorker.safeFailureMessage(failure);

        assertThat(safe).isEqualTo("HTTP transport error");
        assertThat(safe).doesNotContain("hooks.example.com", "access_token", "secret-value");
    }

    @Test
    void timeoutErrorsUseStableSafeClassification() {
        String safe = WebhookWorker.safeFailureMessage(new HttpTimeoutException(
                "request to https://hooks.example.com/events?token=secret timed out"));

        assertThat(safe).isEqualTo("HTTP request timed out");
        assertThat(safe).doesNotContain("hooks.example.com", "secret");
    }

    @Test
    void safeHttpStatusIsPreservedButDecoratedMessagesAreRedacted() {
        assertThat(WebhookWorker.safeFailureMessage(new IllegalStateException("Webhook HTTP 503")))
                .isEqualTo("Webhook HTTP 503");
        assertThat(WebhookWorker.safeFailureMessage(new IllegalStateException(
                "Webhook HTTP 503 from https://hooks.example.com/events?token=secret")))
                .isEqualTo("IllegalStateException");
    }
}
