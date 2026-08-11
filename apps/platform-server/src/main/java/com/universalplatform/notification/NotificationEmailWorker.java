package com.universalplatform.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "platform.notifications.email", name = "enabled", havingValue = "true")
class NotificationEmailWorker {
    private final NotificationDeliveryRepository deliveries;
    private final PlatformNotificationRepository notifications;
    private final JavaMailSender mailSender;
    private final String from;
    private final Clock clock = Clock.systemUTC();

    NotificationEmailWorker(NotificationDeliveryRepository deliveries, PlatformNotificationRepository notifications,
                            JavaMailSender mailSender,
                            @org.springframework.beans.factory.annotation.Value("${platform.notifications.email.from:no-reply@localhost}") String from) {
        this.deliveries = deliveries;
        this.notifications = notifications;
        this.mailSender = mailSender;
        this.from = from;
    }

    @Scheduled(fixedDelayString = "${platform.notifications.email.poll-ms:2000}")
    void poll() {
        Instant now = clock.instant();
        for (UUID id : deliveries.findDueIds(now, PageRequest.of(0, 25))) sendOne(id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendOne(UUID id) {
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
            delivery.failed(exception.getMessage(), now.plusSeconds(seconds));
        }
    }
}
