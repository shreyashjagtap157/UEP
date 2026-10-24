package com.universalplatform.integration;

import com.universalplatform.notification.NotificationPublishedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Transactional event bridge: persist fan-out intent, never call the remote webhook here.
 * The outbox row shares the source notification transaction and survives application restarts.
 */
@Component
class WebhookNotificationListener {
    private final WebhookEventOutboxService outbox;

    WebhookNotificationListener(WebhookEventOutboxService outbox) {
        this.outbox = outbox;
    }

    @EventListener
    public void onNotification(NotificationPublishedEvent event) {
        outbox.enqueue(event);
    }
}
