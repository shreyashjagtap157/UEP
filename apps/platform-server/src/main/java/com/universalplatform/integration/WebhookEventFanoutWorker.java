package com.universalplatform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Expands durable source events into per-subscription deliveries. Delivery intent and the processed
 * marker are committed atomically; HTTP transport remains the responsibility of WebhookWorker.
 */
@Service
class WebhookEventFanoutWorker {
    private static final Logger log = LoggerFactory.getLogger(WebhookEventFanoutWorker.class);

    private final WebhookEventOutboxRepository outbox;
    private final WebhookService webhooks;
    private final TransactionTemplate processingTransaction;
    private final TransactionTemplate failureTransaction;
    private final int batchSize;
    private final Clock clock = Clock.systemUTC();

    WebhookEventFanoutWorker(WebhookEventOutboxRepository outbox, WebhookService webhooks,
                              PlatformTransactionManager transactionManager,
                              @Value("${platform.integrations.webhooks.fanout-batch-size:50}") int batchSize) {
        this.outbox = outbox;
        this.webhooks = webhooks;
        this.processingTransaction = new TransactionTemplate(transactionManager);
        this.processingTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.failureTransaction = new TransactionTemplate(transactionManager);
        this.failureTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
    }

    @Scheduled(fixedDelayString = "${platform.integrations.webhooks.fanout-poll-ms:1000}")
    void poll() {
        Instant now = clock.instant();
        for (UUID id : outbox.findDueIds(now, PageRequest.of(0, batchSize))) {
            processOne(id);
        }
    }

    /** Public within the module for integration tests; every event gets an independent transaction. */
    void processOne(UUID id) {
        try {
            processingTransaction.executeWithoutResult(status -> processWithinTransaction(id));
        } catch (RuntimeException exception) {
            try {
                failureTransaction.executeWithoutResult(status -> outbox.lockById(id).ifPresent(event -> {
                    if (event.processedAt() == null && event.deadLetteredAt() == null) {
                        int attempt = event.attemptCount() + 1;
                        long seconds = Math.min(3600, 5L << Math.min(attempt, 9));
                        event.failed(exception.getMessage(), clock.instant(), clock.instant().plusSeconds(seconds));
                    }
                }));
            } catch (RuntimeException failureRecordingException) {
                exception.addSuppressed(failureRecordingException);
            }
            log.warn("Webhook fan-out event {} failed", id, exception);
        }
    }

    private void processWithinTransaction(UUID id) {
        WebhookEventOutbox event = outbox.lockById(id).orElse(null);
        if (event == null || event.processedAt() != null || event.deadLetteredAt() != null
                || event.availableAt().isAfter(clock.instant())) {
            return;
        }

        Map<String, Object> payload = Map.of(
                "aggregateId", event.aggregateId().toString(),
                "title", event.title(),
                "body", event.body(),
                "occurredAt", event.occurredAt().toString());
        webhooks.publishForTenantAt(event.tenantId(), event.sourceOutboxId(),
                event.eventType(), payload, event.occurredAt());
        event.processed(clock.instant());
    }
}
