package com.universalplatform.integration;

import com.universalplatform.notification.NotificationPublishedEvent;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores webhook fan-out work in the transaction that publishes the source notification event.
 * This queue records intent only; network delivery is delegated to WebhookEventFanoutWorker.
 */
@Service
class WebhookEventOutboxService {
    private final WebhookEventOutboxRepository outbox;
    private final Clock clock = Clock.systemUTC();

    WebhookEventOutboxService(WebhookEventOutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Transactional
    void enqueue(NotificationPublishedEvent event) {
        outbox.enqueueIfAbsent(UUID.randomUUID(), event.tenantId(), event.sourceOutboxId(),
                event.aggregateId(), event.eventType(), event.title(), event.body(),
                event.occurredAt(), clock.instant());
    }
}
