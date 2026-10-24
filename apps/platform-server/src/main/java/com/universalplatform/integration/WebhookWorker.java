package com.universalplatform.integration;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class WebhookWorker {
    private final WebhookDeliveryRepository deliveries;
    private final WebhookSubscriptionRepository subs;
    private final IntegrationSecretCipher cipher;
    private final WebhookWorkerLock workerLock;
    private final OutboundEndpointValidator endpointValidator;
    private final TransactionTemplate deliveryTransaction;
    private final java.net.http.HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final Clock clock = Clock.systemUTC();

    WebhookWorker(WebhookDeliveryRepository d, WebhookSubscriptionRepository s, IntegrationSecretCipher c,
            WebhookWorkerLock workerLock, OutboundEndpointValidator endpointValidator,
            PlatformTransactionManager transactionManager) {
        deliveries = d;
        subs = s;
        cipher = c;
        this.workerLock = workerLock;
        this.endpointValidator = endpointValidator;
        this.deliveryTransaction = new TransactionTemplate(transactionManager);
        this.deliveryTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${platform.integrations.webhooks.poll-ms:2000}")
    void poll() {
        Instant n = clock.instant();
        for (UUID id : deliveries.findDue(n, PageRequest.of(0, 25)))
            processOne(id);
    }

    void processOne(UUID id) {
        deliveryTransaction.executeWithoutResult(status -> deliverInTransaction(id));
    }

    private void deliverInTransaction(UUID id) {
        WebhookDelivery d = deliveries.lock(id).orElse(null);
        if (d == null || !isDue(d, clock.instant()))
            return;
        // Cross-instance co-claim per tenant: two workers seeing the same due row are serialized by
        // the transaction-scoped advisory lock before either performs delivery work.
        workerLock.acquire(d.tenantId());
        d = deliveries.lock(id).orElse(null);
        // Another worker may have completed a failed attempt and moved nextAttemptAt into the
        // future while this worker waited for the advisory lock. Recheck the persisted state under
        // the row lock or concurrent pollers can defeat backoff and duplicate a delivery attempt.
        if (d == null || !isDue(d, clock.instant()))
            return;
        WebhookSubscription s = subs.findById(d.subscriptionId()).orElse(null);
        if (s == null || !s.enabled()) {
            d.failed("Subscription unavailable", clock.instant());
            return;
        }
        try {
            long epoch = clock.instant().getEpochSecond();
            String sig = WebhookService.signature(cipher.decrypt(s.secretCiphertext()), epoch, d.payloadJson());
            URI endpoint = endpointValidator.validate(s.callbackUrl());
            HttpRequest r = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("X-UEP-Event-ID", d.eventId().toString())
                    .header("X-UEP-Event", d.eventType())
                    .header("X-UEP-Signature", "t=" + epoch + ",v1=" + sig)
                    .POST(HttpRequest.BodyPublishers.ofString(d.payloadJson()))
                    .build();
            HttpResponse<Void> response = http.send(r, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2)
                throw new IllegalStateException("Webhook HTTP " + response.statusCode());
            d.success(clock.instant());
            s.delivered(clock.instant());
        } catch (Exception e) {
            d.failed(e.getMessage(), clock.instant());
            s.failed(clock.instant(), e.getMessage());
        }
    }

    private static boolean isDue(WebhookDelivery delivery, Instant now) {
        return delivery.deliveredAt() == null
                && delivery.attemptCount() < 12
                && !delivery.nextAttemptAt().isAfter(now);
    }
}
