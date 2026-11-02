package com.universalplatform.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "webhook_subscription", uniqueConstraints = @UniqueConstraint(
        name = "uq_webhook_tenant_url", columnNames = {"tenant_id", "callback_url"}))
class WebhookSubscription {
    private static final Set<String> SAFE_FAILURE_MESSAGES = Set.of(
            "Subscription unavailable",
            "Delivery interrupted",
            "Outbound endpoint validation failed",
            "HTTP request timed out",
            "HTTP transport error");

    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 1000) private String callbackUrl;
    @Column(nullable = false, length = 120) private String eventFilter;
    @Column(nullable = false, length = 500) private String secretCiphertext;
    @Column(nullable = false) private boolean enabled;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    private Instant lastDeliveredAt;
    private Instant lastFailedAt;
    @Column(length = 1000) private String lastError;
    @Version private long version;

    protected WebhookSubscription() {}

    WebhookSubscription(UUID id, UUID tenantId, String callbackUrl, String eventFilter,
                        String secretCiphertext, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.callbackUrl = callbackUrl;
        this.eventFilter = eventFilter;
        this.secretCiphertext = secretCiphertext;
        this.enabled = true;
        this.createdAt = now;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    String callbackUrl() { return callbackUrl; }
    String eventFilter() { return eventFilter; }
    String secretCiphertext() { return secretCiphertext; }
    boolean enabled() { return enabled; }
    long version() { return version; }
    String lastError() { return lastError; }

    void disable() { enabled = false; }

    void delivered(Instant now) {
        lastDeliveredAt = now;
        lastError = null;
    }

    void failed(Instant now, String error) {
        lastFailedAt = now;
        lastError = safeFailure(error);
    }

    private static String safeFailure(String value) {
        if (value != null && value.matches("[A-Za-z0-9_$]{1,160}")) return value;
        if (value != null && value.matches("Webhook HTTP [1-5][0-9]{2}")) return value;
        return value != null && SAFE_FAILURE_MESSAGES.contains(value) ? value : "WebhookSubscriptionFailure";
    }
}
