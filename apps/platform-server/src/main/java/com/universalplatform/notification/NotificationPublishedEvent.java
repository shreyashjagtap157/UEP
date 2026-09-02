package com.universalplatform.notification;
import java.time.Instant; import java.util.UUID;
public record NotificationPublishedEvent(UUID tenantId, UUID sourceOutboxId, UUID aggregateId, String eventType, String title, String body, Instant occurredAt) {}
