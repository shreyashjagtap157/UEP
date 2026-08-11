package com.universalplatform.notification;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {
    @Query("""
            select d.id from NotificationDelivery d
            where d.status in (com.universalplatform.notification.NotificationDeliveryStatus.PENDING,
                               com.universalplatform.notification.NotificationDeliveryStatus.FAILED)
              and d.nextAttemptAt <= :now and d.attemptCount < 8
            order by d.nextAttemptAt asc
            """)
    List<UUID> findDueIds(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from NotificationDelivery d where d.id = :id")
    Optional<NotificationDelivery> lockById(@Param("id") UUID id);

    long countByTenantIdAndStatusIn(UUID tenantId, java.util.Collection<NotificationDeliveryStatus> statuses);
    Optional<NotificationDelivery> findByIdempotencyKey(String idempotencyKey);
}
