package com.universalplatform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universalplatform.audit.AuditService;
import com.universalplatform.identity.*;
import com.universalplatform.security.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookService {
    private final TenantContext tenants;
    private final ActorContext actor;
    private final AuthorizationService auth;
    private final WebhookSubscriptionRepository subs;
    private final WebhookDeliveryRepository deliveries;
    private final IntegrationSecretCipher cipher;
    private final AuditService audit;
    private final OutboundEndpointValidator endpointValidator;
    private final ObjectMapper json = new ObjectMapper();
    private final Clock clock = Clock.systemUTC();
    private final SecureRandom random = new SecureRandom();

    WebhookService(TenantContext tenants, ActorContext actor, AuthorizationService auth,
            WebhookSubscriptionRepository subs, WebhookDeliveryRepository deliveries,
            IntegrationSecretCipher cipher, AuditService audit, OutboundEndpointValidator endpointValidator) {
        this.tenants = tenants;
        this.actor = actor;
        this.auth = auth;
        this.subs = subs;
        this.deliveries = deliveries;
        this.cipher = cipher;
        this.audit = audit;
        this.endpointValidator = endpointValidator;
    }

    @Transactional
    public SubscriptionView create(String url, String eventFilter) {
        auth.require(PermissionKey.WEBHOOKS_MANAGE);
        UUID t = tenants.requireTenantId();
        String u = endpointValidator.validate(url).toString();
        String e = normalizeFilter(eventFilter);
        String secret = generateSecret();
        WebhookSubscription s = subs.save(new WebhookSubscription(UUID.randomUUID(), t, u, e, cipher.encrypt(secret), clock.instant()));
        audit.record(t, actor.requireSubject(), "WEBHOOK_CREATED", "webhook_subscription", s.id().toString());
        return new SubscriptionView(s.id(), s.callbackUrl(), s.eventFilter(), true, secret, s.version());
    }

    @Transactional(readOnly = true)
    public List<SubscriptionView> list() {
        auth.require(PermissionKey.WEBHOOKS_VIEW);
        UUID t = tenants.requireTenantId();
        return subs.findAllByTenantIdAndEnabledTrue(t).stream()
                .map(s -> new SubscriptionView(s.id(), s.callbackUrl(), s.eventFilter(), true, null, s.version()))
                .toList();
    }

    @Transactional
    public void revoke(UUID id, long expectedVersion) {
        auth.require(PermissionKey.WEBHOOKS_MANAGE);
        WebhookSubscription s = subs.findByTenantIdAndId(tenants.requireTenantId(), id)
                .orElseThrow(() -> new IllegalArgumentException("Webhook subscription not found"));
        if (s.version() != expectedVersion)
            throw new IllegalArgumentException("Webhook was modified by another request");
        s.disable();
        audit.record(s.tenantId(), actor.requireSubject(), "WEBHOOK_REVOKED", "webhook_subscription", id.toString());
    }

    /** User-invoked publish (e.g. /webhooks/test): guarded by the same permission as webhook management. */
    @Transactional
    public void publish(String eventType, Map<String, Object> payload) {
        auth.require(PermissionKey.WEBHOOKS_MANAGE);
        UUID t = tenants.requireTenantId();
        publishForTenant(t, UUID.randomUUID(), eventType, payload);
    }

    @Transactional
    public void publishForTenant(UUID tenantId, UUID eventId, String eventType, Map<String, Object> payload) {
        publishForTenantAt(tenantId, eventId, eventType, payload, clock.instant());
    }

    @Transactional
    public void publishForTenantAt(UUID tenantId, UUID eventId, String eventType,
                                   Map<String, Object> payload, Instant occurredAt) {
        String e = normalizeEvent(eventType);
        Instant eventTime = occurredAt == null ? clock.instant() : occurredAt;
        String body;
        try {
            body = json.writeValueAsString(Map.of("id", eventId, "eventType", e, "occurredAt", eventTime.toString(),
                    "data", payload == null ? Map.of() : payload));
        } catch (Exception x) {
            throw new IllegalStateException("Webhook event serialization failed", x);
        }
        Instant nextAttemptAt = clock.instant();
        for (WebhookSubscription subscription : subs.findAllByTenantIdAndEnabledTrue(tenantId)) {
            if (!matches(subscription.eventFilter(), e))
                continue;
            // The unique (subscription,event) constraint and ON CONFLICT make duplicate event
            // publication idempotent without swallowing real persistence failures.
            deliveries.enqueueIfAbsent(UUID.randomUUID(), tenantId, subscription.id(), eventId,
                    e, body, nextAttemptAt);
        }
    }

    private static boolean matches(String filter, String event) {
        return "*".equals(filter) || filter.equalsIgnoreCase(event);
    }

    private String generateSecret() {
        byte[] b = new byte[24];
        random.nextBytes(b);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String normalizeFilter(String v) {
        String x = v == null || v.isBlank() ? "*" : v.trim();
        if (x.length() > 64)
            throw new IllegalArgumentException("Event filter is too long");
        return x;
    }

    private static String normalizeEvent(String v) {
        String x = v == null ? "" : v.trim();
        if (x.isBlank() || x.length() > 64)
            throw new IllegalArgumentException("Event type is required");
        return x;
    }

    static String signature(String secret, long epoch, String body) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(m.doFinal((epoch + "." + body).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record SubscriptionView(UUID id, String callbackUrl, String eventFilter, boolean enabled, String secret, long version) {}
}
