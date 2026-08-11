package com.universalplatform.notification;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {
    Optional<NotificationOutbox> findByTenantIdAndDeduplicationKey(UUID tenantId, String key);

    @Query("""
            select o.id from NotificationOutbox o
            where o.processedAt is null and o.deadLetteredAt is null and o.availableAt <= :now and o.attemptCount < 12
            order by o.occurredAt asc
            """)
    List<UUID> findDueIds(@Param("now") Instant now, org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from NotificationOutbox o where o.id = :id")
    Optional<NotificationOutbox> lockById(@Param("id") UUID id);

    List<NotificationOutbox> findAllByTenantIdAndEventTypeAndAggregateIdAndProcessedAtIsNull(UUID tenantId, String eventType, UUID aggregateId);
    long countByTenantIdAndProcessedAtIsNullAndDeadLetteredAtIsNull(UUID tenantId);
    long countByTenantIdAndDeadLetteredAtIsNotNull(UUID tenantId);
}
