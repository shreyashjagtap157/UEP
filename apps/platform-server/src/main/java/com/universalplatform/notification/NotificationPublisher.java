package com.universalplatform.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

/** Public durable-notification boundary used by domain modules inside their business transaction. */
@Service
public class NotificationPublisher {
    private final NotificationOutboxRepository outbox;
    private final NotificationOutboxRecipientRepository recipients;
    private final Clock clock = Clock.systemUTC();
    private final ApplicationEventPublisher events;

    NotificationPublisher(NotificationOutboxRepository outbox, NotificationOutboxRecipientRepository recipients, ApplicationEventPublisher events) {
        this.outbox = outbox;
        this.recipients = recipients;
        this.events = events;
    }

    @Transactional
    public void enqueue(NotificationRequest request) {
        if (request.recipients() == null || request.recipients().isEmpty()) return;
        String deduplicationKey = required(request.deduplicationKey(), "Deduplication key", 240);
        if (outbox.findByTenantIdAndDeduplicationKey(request.tenantId(), deduplicationKey).isPresent()) return;
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        NotificationOutbox event = outbox.save(new NotificationOutbox(id, request.tenantId(), request.eventType(),
                required(request.aggregateType(), "Aggregate type", 64), request.aggregateId(),
                required(request.title(), "Notification title", 240), required(request.body(), "Notification body", 4000),
                request.priority() == null ? NotificationPriority.NORMAL : request.priority(),
                nullable(request.resourceType(), 64), request.resourceId(), deduplicationKey, now,
                request.availableAt() == null ? now : request.availableAt()));
        Map<UUID, Recipient> unique = new LinkedHashMap<>();
        for (Recipient recipient : request.recipients()) {
            if (recipient != null && recipient.membershipId() != null) unique.putIfAbsent(recipient.membershipId(), recipient);
        }
        recipients.saveAll(unique.values().stream()
                .map(recipient -> new NotificationOutboxRecipient(event.id(), recipient.membershipId(), request.tenantId(),
                        nullable(recipient.email(), 320)))
                .toList());
        events.publishEvent(new NotificationPublishedEvent(request.tenantId(), event.id(), request.aggregateId(), request.eventType().name(), request.title(), request.body(), now));
    }

    @Transactional
    public void cancelPending(UUID tenantId, NotificationEventType eventType, UUID aggregateId, String reason) {
        Instant now = clock.instant();
        for (NotificationOutbox event : outbox.findAllByTenantIdAndEventTypeAndAggregateIdAndProcessedAtIsNull(
                tenantId, eventType.name(), aggregateId)) {
            event.processed(now);
        }
    }

    private static String required(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(label + " is too long");
        return normalized;
    }

    private static String nullable(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("Value is too long");
        return normalized;
    }

    public record Recipient(UUID membershipId, String email) {}

    public record NotificationRequest(
            UUID tenantId,
            NotificationEventType eventType,
            String aggregateType,
            UUID aggregateId,
            String title,
            String body,
            NotificationPriority priority,
            String resourceType,
            UUID resourceId,
            String deduplicationKey,
            Instant availableAt,
            Collection<Recipient> recipients) {}
}
