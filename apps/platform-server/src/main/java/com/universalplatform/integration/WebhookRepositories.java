package com.universalplatform.integration;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {
    List<WebhookSubscription> findAllByTenantIdAndEnabledTrue(UUID tenantId);

    Optional<WebhookSubscription> findByTenantIdAndId(UUID tenantId, UUID id);
}

interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    Optional<WebhookDelivery> findBySubscriptionIdAndEventId(UUID subscriptionId, UUID eventId);

    /**
     * Idempotent enqueue at the database uniqueness boundary. A duplicate event is a no-op,
     * while genuine SQL/connection failures propagate to the publisher.
     */
    @Modifying
    @Query(value = """
            INSERT INTO webhook_delivery(
                id, tenant_id, subscription_id, event_id, event_type, payload_json, next_attempt_at
            ) VALUES (
                :id, :tenantId, :subscriptionId, :eventId, :eventType, :payloadJson, :nextAttemptAt
            )
            ON CONFLICT (subscription_id, event_id) DO NOTHING
            """, nativeQuery = true)
    int enqueueIfAbsent(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("subscriptionId") UUID subscriptionId,
            @Param("eventId") UUID eventId,
            @Param("eventType") String eventType,
            @Param("payloadJson") String payloadJson,
            @Param("nextAttemptAt") Instant nextAttemptAt);

    @Query("select d.id from WebhookDelivery d where d.deliveredAt is null and d.nextAttemptAt <= :now and d.attemptCount < 12 order by d.nextAttemptAt")
    List<UUID> findDue(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from WebhookDelivery d where d.id=:id")
    Optional<WebhookDelivery> lock(@Param("id") UUID id);
}

interface WebhookEventOutboxRepository extends JpaRepository<WebhookEventOutbox, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO webhook_event_outbox(
                id, tenant_id, source_outbox_id, aggregate_id, event_type, title, body,
                occurred_at, available_at
            ) VALUES (
                :id, :tenantId, :sourceOutboxId, :aggregateId, :eventType, :title, :body,
                :occurredAt, :availableAt
            )
            ON CONFLICT (tenant_id, source_outbox_id) DO NOTHING
            """, nativeQuery = true)
    int enqueueIfAbsent(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("sourceOutboxId") UUID sourceOutboxId,
            @Param("aggregateId") UUID aggregateId,
            @Param("eventType") String eventType,
            @Param("title") String title,
            @Param("body") String body,
            @Param("occurredAt") Instant occurredAt,
            @Param("availableAt") Instant availableAt);

    @Query("""
            select event.id from WebhookEventOutbox event
            where event.processedAt is null
              and event.deadLetteredAt is null
              and event.availableAt <= :now
            order by event.availableAt
            """)
    List<UUID> findDueIds(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from WebhookEventOutbox event where event.id = :id")
    Optional<WebhookEventOutbox> lockById(@Param("id") UUID id);
}
