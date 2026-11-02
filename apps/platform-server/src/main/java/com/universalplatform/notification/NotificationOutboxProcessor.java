package com.universalplatform.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
 * Materializes due outbox events into per-recipient notifications and email deliveries.
 * Each due event is processed inside its own transaction via {@link TransactionTemplate}
 * so the pessimistic row lock and the processed/failed state transitions actually persist
 * (a self-invoked {@code @Transactional} method from {@code poll()} would bypass the proxy).
 */
@Service
class NotificationOutboxProcessor {
    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxProcessor.class);

    private final NotificationOutboxRepository outbox;
    private final NotificationOutboxRecipientRepository recipients;
    private final NotificationPreferenceRepository preferences;
    private final PlatformNotificationRepository notifications;
    private final NotificationDeliveryRepository deliveries;
    private final TransactionTemplate processingTransaction;
    private final TransactionTemplate failureTransaction;
    private final boolean emailDeliveryEnabled;
    private final Clock clock = Clock.systemUTC();

    NotificationOutboxProcessor(NotificationOutboxRepository outbox,
                                NotificationOutboxRecipientRepository recipients,
                                NotificationPreferenceRepository preferences,
                                PlatformNotificationRepository notifications,
                                NotificationDeliveryRepository deliveries,
                                PlatformTransactionManager transactionManager,
                                @Value("${platform.notifications.email.enabled:false}") boolean emailDeliveryEnabled) {
        this.outbox = outbox;
        this.recipients = recipients;
        this.preferences = preferences;
        this.notifications = notifications;
        this.deliveries = deliveries;
        this.processingTransaction = new TransactionTemplate(transactionManager);
        this.processingTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.failureTransaction = new TransactionTemplate(transactionManager);
        this.failureTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.emailDeliveryEnabled = emailDeliveryEnabled;
    }

    @Scheduled(fixedDelayString = "${platform.notifications.outbox-poll-ms:1000}")
    void poll() {
        Instant now = clock.instant();
        for (UUID id : outbox.findDueIds(now, PageRequest.of(0, 50))) {
            try {
                processOne(id);
            } catch (RuntimeException exception) {
                // One poisoned event must not stop the batch; its failure state is recorded
                // inside the per-event transaction so it backs off via availableAt.
                log.warn("Notification outbox event {} failed to process", id, exception);
            }
        }
    }

    /** Public for tests; executes the per-event work in a separate transaction each call. */
    public void processOne(UUID id) {
        try {
            processingTransaction.executeWithoutResult(status -> processWithinTransaction(id, status));
        } catch (RuntimeException processingFailure) {
            Throwable reason = processingFailure instanceof NotificationOutboxProcessingException
                    && processingFailure.getCause() != null
                    ? processingFailure.getCause() : processingFailure;
            try {
                failureTransaction.executeWithoutResult(status -> outbox.lockById(id).ifPresent(event -> {
                    if (event.processedAt() == null && event.deadLetteredAt() == null) {
                        Instant now = clock.instant();
                        int attempt = event.attemptCount() + 1;
                        long seconds = Math.min(3600, 5L << Math.min(attempt, 9));
                        // Persist only a safe failure class, not provider/database messages or payload data.
                        event.failed(reason.getClass().getSimpleName(), now, now.plusSeconds(seconds));
                    }
                }));
            } catch (RuntimeException persistenceFailure) {
                processingFailure.addSuppressed(persistenceFailure);
                log.error("Could not persist notification outbox retry state for event {}", id, processingFailure);
            }
            log.warn("Notification outbox event {} failed; retry state is handled independently",
                    id, reason);
        }
    }

    private void processWithinTransaction(UUID id, org.springframework.transaction.TransactionStatus status) {
        NotificationOutbox event = outbox.lockById(id).orElse(null);
        if (event == null || event.processedAt() != null || event.deadLetteredAt() != null
                || event.availableAt().isAfter(clock.instant())) return;
        Instant now = clock.instant();
        try {
            for (NotificationOutboxRecipient recipient : recipients.findAllByIdOutboxId(event.id())) {
                materialize(event, recipient, now);
            }
            event.processed(now);
        } catch (RuntimeException failure) {
            status.setRollbackOnly();
            throw new NotificationOutboxProcessingException(failure);
        }
    }

    private static final class NotificationOutboxProcessingException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private NotificationOutboxProcessingException(RuntimeException cause) {
            super(cause.getClass().getSimpleName(), cause);
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
