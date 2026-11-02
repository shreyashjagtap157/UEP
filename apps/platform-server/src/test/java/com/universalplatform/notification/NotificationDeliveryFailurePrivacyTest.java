package com.universalplatform.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationDeliveryFailurePrivacyTest {
    @Test
    void failedEmailDeliveryPersistsOnlyAnExceptionCategory() {
        Instant now = Instant.parse("2026-11-02T00:00:00Z");
        NotificationDelivery delivery = new NotificationDelivery(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "learner@example.test", "email:privacy-test", true, now);

        delivery.failed("SMTP rejected learner@example.test at smtp.private.test; auth token=secret-value",
                now, now.plusSeconds(30));

        assertThat(delivery.status()).isEqualTo(NotificationDeliveryStatus.FAILED);
        assertThat(delivery.lastError()).isEqualTo("EmailDeliveryFailure");
        assertThat(delivery.lastError()).doesNotContain(
                "learner@example.test", "smtp.private.test", "secret-value");
    }

    @Test
    void safeEmailFailureCategoryIsRetainedWithoutProviderMessage() {
        Instant now = Instant.parse("2026-11-02T00:00:00Z");
        NotificationDelivery delivery = new NotificationDelivery(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "learner@example.test", "email:privacy-category", true, now);

        delivery.failed("MailSendException", now, now.plusSeconds(30));

        assertThat(delivery.lastError()).isEqualTo("MailSendException");
    }
}
