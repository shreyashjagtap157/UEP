package com.universalplatform.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class NotificationOutboxProcessor {
    private final NotificationOutboxRepository outbox;
    private final NotificationOutboxRecipientRepository recipients;
    private final NotificationPreferenceRepository preferences;
    private final PlatformNotificationRepository notifications;
    private final NotificationDeliveryRepository deliveries;
    private final boolean emailDeliveryEnabled;
    private final Clock clock = Clock.systemUTC();

    NotificationOutboxProcessor(NotificationOutboxRepository outbox,
                                NotificationOutboxRecipientRepository recipients,
                                NotificationPreferenceRepository preferences,
                                PlatformNotificationRepository notifications,
                                NotificationDeliveryRepository deliveries,
                                @Value("${platform.notifications.email.enabled:false}") boolean emailDeliveryEnabled) {
        this.outbox = outbox;
        this.recipients = recipients;
        this.preferences = preferences;
        this.notifications = notifications;
        this.deliveries = deliveries;
        this.emailDeliveryEnabled = emailDeliveryEnabled;
    }

    @Scheduled(fixedDelayString = "${platform.notifications.outbox-poll-ms:1000}")
    void poll() {
        Instant now = clock.instant();
        for (UUID id : outbox.findDueIds(now, PageRequest.of(0, 50))) processOne(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOne(UUID id) {
        NotificationOutbox event = outbox.lockById(id).orElse(null);
        if (event == null || event.processedAt() != null || event.deadLetteredAt() != null || event.availableAt().isAfter(clock.instant())) return;
        Instant now = clock.instant();
        try {
            for (NotificationOutboxRecipient recipient : recipients.findAllByIdOutboxId(event.id())) {
                materialize(event, recipient, now);
            }
            event.processed(now);
        } catch (RuntimeException exception) {
            int attempt = event.attemptCount() + 1;
            long seconds = Math.min(3600, 5L << Math.min(attempt, 9));
            event.failed(exception.getMessage(), now, now.plusSeconds(seconds));
        }
    }

    private void materialize(NotificationOutbox event, NotificationOutboxRecipient recipient, Instant now) {
        Preference effective = preference(event.tenantId(), recipient.membershipId(), event.eventType());
        if (!effective.inApp() && !effective.email()) return;

        PlatformNotification notification = notifications.findBySourceOutboxIdAndMembershipId(event.id(), recipient.membershipId())
                .orElseGet(() -> notifications.save(new PlatformNotification(UUID.randomUUID(), event.tenantId(), recipient.membershipId(),
                        event.id(), event.eventType(), event.title(), event.body(), event.resourceType(), event.resourceId(),
                        event.priority(), effective.inApp(), now)));

        if (effective.email() && recipient.email() != null && !recipient.email().isBlank()) {
            String idempotencyKey = "email:" + event.id() + ":" + recipient.membershipId();
            if (deliveries.findByIdempotencyKey(idempotencyKey).isEmpty()) {
                deliveries.save(new NotificationDelivery(UUID.randomUUID(), event.tenantId(), notification.id(),
                        recipient.email(), idempotencyKey, emailDeliveryEnabled, now));
            }
        }
    }

    private Preference preference(UUID tenantId, UUID membershipId, NotificationEventType eventType) {
        return preferences.findByTenantIdAndIdMembershipIdAndIdEventType(tenantId, membershipId, eventType.name())
                .map(item -> new Preference(item.inAppEnabled(), item.emailEnabled()))
                .orElse(new Preference(true, true));
    }


    record Preference(boolean inApp, boolean email) {}
}
