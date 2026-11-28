package com.universalplatform.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "platform.notifications.outbox-poll-ms=3600000",
        "platform.notifications.email.poll-ms=3600000",
        "platform.notifications.email.enabled=true"
})
class NotificationEmailWorkerIntegrationTest {
    private UUID tenantId;
    private UUID userId;
    private UUID membershipId;
    private String subject;
    private String recipientEmail;

    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationPublisher publisher;
    @Autowired NotificationOutboxProcessor processor;
    @Autowired NotificationEmailWorker worker;
    @Autowired NotificationDeliveryRepository deliveries;

    @MockitoBean
    JavaMailSender mailSender;

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        membershipId = UUID.randomUUID();
        subject = "notification-email-" + suffix;
        recipientEmail = "recipient-" + suffix + "@example.test";

        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, ?, 'Notification Email Test', 'ACTIVE')",
                tenantId, "notification-email-" + suffix);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, email, display_name, status) VALUES (?, ?, ?, 'Email Recipient', 'ACTIVE')",
                userId, subject, recipientEmail);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')",
                membershipId, tenantId, userId);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM audit_event WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM notification_delivery WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM notification WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM notification_outbox_recipient WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM notification_outbox WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM notification_preference WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM tenant_membership WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM tenant WHERE id = ?", tenantId);
        jdbc.update("DELETE FROM user_account WHERE id = ?", userId);
    }

    @Test
    void sendsEmailAndPersistsSmtpAcceptance() {
        UUID deliveryId = enqueueEmail();

        worker.sendOne(deliveryId);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly(recipientEmail);
        assertThat(sent.getValue().getSubject()).isEqualTo("New UEP message");
        assertThat(sent.getValue().getText()).contains("Sign in to UEP");

        assertThat(status(deliveryId)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject(
                "SELECT sent_at IS NOT NULL FROM notification_delivery WHERE id = ?",
                Boolean.class, deliveryId)).isTrue();
        assertThat(attemptCount(deliveryId)).isZero();
    }

    @Test
    void persistsRetryBackoffAndDeadLettersAfterEightFailedAttempts() {
        doThrow(new MailSendException("SMTP rejected " + recipientEmail + "; auth token=private-secret"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        UUID deliveryId = enqueueEmail();

        assertThat(deliveries.findDueIds(Instant.now(), org.springframework.data.domain.PageRequest.of(0, 50)))
                .contains(deliveryId);
        Instant before = Instant.now();
        worker.sendOne(deliveryId);

        assertThat(status(deliveryId)).isEqualTo("FAILED");
        assertThat(attemptCount(deliveryId)).isEqualTo(1);
        String safeFailure = jdbc.queryForObject(
                "SELECT last_error FROM notification_delivery WHERE id = ?", String.class, deliveryId);
        assertThat(safeFailure).isEqualTo("MailSendException");
        assertThat(safeFailure).doesNotContain(recipientEmail, "private-secret");
        Instant nextAttempt = jdbc.queryForObject(
                "SELECT next_attempt_at FROM notification_delivery WHERE id = ?",
                (rs, row) -> rs.getTimestamp(1).toInstant(), deliveryId);
        assertThat(nextAttempt).isAfter(before);
        assertThat(deliveries.findDueIds(Instant.now(), org.springframework.data.domain.PageRequest.of(0, 50)))
                .doesNotContain(deliveryId);

        // A second poller may already have captured this ID before the first
        // poller failed it. The post-lock check must preserve the new backoff.
        worker.sendOne(deliveryId);
        assertThat(attemptCount(deliveryId)).isEqualTo(1);
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));

        for (int attempt = 1; attempt < 8; attempt++) {
            jdbc.update("UPDATE notification_delivery SET next_attempt_at = current_timestamp - interval '1 second' WHERE id = ?",
                    deliveryId);
            worker.sendOne(deliveryId);
        }

        assertThat(status(deliveryId)).isEqualTo("DEAD_LETTERED");
        assertThat(attemptCount(deliveryId)).isEqualTo(8);

        // A stale candidate selected before another worker dead-lettered it
        // must not resurrect the delivery.
        worker.sendOne(deliveryId);
        verify(mailSender, times(8)).send(any(SimpleMailMessage.class));
    }

    private UUID enqueueEmail() {
        UUID messageId = UUID.randomUUID();
        publisher.enqueue(new NotificationPublisher.NotificationRequest(
                tenantId, NotificationEventType.MESSAGE_RECEIVED, "message", messageId,
                "New UEP message", "A participant sent you a message. Sign in to UEP to read it.",
                NotificationPriority.NORMAL, "conversation", UUID.randomUUID(),
                "email-worker-test:" + messageId, null,
                List.of(new NotificationPublisher.Recipient(membershipId, recipientEmail))));
        UUID outboxId = jdbc.queryForObject(
                "SELECT id FROM notification_outbox WHERE tenant_id = ? AND aggregate_id = ?",
                UUID.class, tenantId, messageId);
        processor.processOne(outboxId);
        return jdbc.queryForObject(
                "SELECT id FROM notification_delivery WHERE tenant_id = ?",
                UUID.class, tenantId);
    }

    private String status(UUID deliveryId) {
        return jdbc.queryForObject("SELECT status FROM notification_delivery WHERE id = ?", String.class, deliveryId);
    }

    private int attemptCount(UUID deliveryId) {
        return jdbc.queryForObject("SELECT attempt_count FROM notification_delivery WHERE id = ?", Integer.class, deliveryId);
    }
}
