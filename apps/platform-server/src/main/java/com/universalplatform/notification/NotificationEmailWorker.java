package com.universalplatform.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends pending email deliveries through SMTP. Each delivery is attempted inside its own
 * transaction via {@link TransactionTemplate} so the pessimistic lock applies and SENT / FAILED /
 * DEAD_LETTERED transitions with backoff state actually persist (invoking a proxied
 * {@code @Transactional} method from {@code poll()} would bypass the transaction entirely
 * and re-send the same email on every poll).
 */
@Service
@ConditionalOnProperty(prefix = "platform.notifications.email", name = "enabled", havingValue = "true")
class NotificationEmailWorker {
    private static final Logger log = LoggerFactory.getLogger(NotificationEmailWorker.class);

    private final NotificationDeliveryRepository deliveries;
    private final PlatformNotificationRepository notifications;
    private final TransactionTemplate transactions;
    private final JavaMailSender mailSender;
    private final String from;
    private final Clock clock = Clock.systemUTC();

    NotificationEmailWorker(NotificationDeliveryRepository deliveries, PlatformNotificationRepository notifications,
                            TransactionTemplate transactions, JavaMailSender mailSender,
                            @org.springframework.beans.factory.annotation.Value("${platform.notifications.email.from:no-reply@localhost}") String from) {
        this.deliveries = deliveries;
        this.notifications = notifications;
        this.transactions = transactions;
        this.mailSender = mailSender;
        this.from = from;
    }

    @Scheduled(fixedDelayString = "${platform.notifications.email.poll-ms:2000}")
    void poll() {
        Instant now = clock.instant();
        for (UUID id : deliveries.findDueIds(now, PageRequest.of(0, 25))) {
            try {
                sendOne(id);
            } catch (RuntimeException exception) {
                // A transient infrastructure failure must not stop the batch; the failure state
                // (attempt count, next attempt, dead-letter after 8) persists in the per-delivery tx.
                log.warn("Notification email delivery {} failed", id, exception);
            }
        }
    }

    /** Public for tests; executes the per-delivery work in a separate transaction each call. */
    public void sendOne(UUID id) {
        transactions.executeWithoutResult(status -> sendWithinTransaction(id));
    }

    private void sendWithinTransaction(UUID id) {
        NotificationDelivery delivery = deliveries.lockById(id).orElse(null);
        if (delivery == null || delivery.status() == NotificationDeliveryStatus.SENT || delivery.status() == NotificationDeliveryStatus.SKIPPED) return;
        Instant now = clock.instant();
        try {
            PlatformNotification notification = notifications.findById(delivery.notificationId())
                    .orElseThrow(() -> new IllegalStateException("Notification delivery source no longer exists"));
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(delivery.destination());
            message.setSubject(notification.title());
            message.setText(notification.body());
            mailSender.send(message);
            delivery.sent(now);
        } catch (RuntimeException exception) {
            int exponent = Math.min(delivery.attemptCount(), 8);
            long seconds = Math.min(3600, 10L << exponent);
            delivery.failed(exception.getMessage(), now, now.plusSeconds(seconds));
        }
    }
}
