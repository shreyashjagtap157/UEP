package com.universalplatform.messaging;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MessageReportRepository extends JpaRepository<MessageReport, UUID> {
    boolean existsByTenantIdAndMessageIdAndReporterMembershipId(
            UUID tenantId, UUID messageId, UUID reporterMembershipId);

    Optional<MessageReport> findByTenantIdAndId(UUID tenantId, UUID id);

    Page<MessageReport> findAllByTenantId(UUID tenantId, Pageable pageable);

    @Modifying
    @Query(value = """
            insert into message_rate_window (
                tenant_id, membership_id, window_type, window_start, message_count, report_count
            )
            values (:tenantId, :membershipId, 'HOUR', date_trunc('hour', current_timestamp), 0, 1)
            on conflict (tenant_id, membership_id, window_type, window_start)
            do update set report_count = message_rate_window.report_count + 1
            where message_rate_window.report_count < :maxCount
            """, nativeQuery = true)
    int consumeReportPermit(@Param("tenantId") UUID tenantId,
                            @Param("membershipId") UUID membershipId,
                            @Param("maxCount") int maxCount);

    @Modifying
    @Query(value = """
            delete from message_report r
            where r.id in (
                select r2.id from message_report r2
                where r2.tenant_id = :tenantId and r2.created_at < :cutoff
                order by r2.created_at, r2.id
                limit :batchSize
            )
            """, nativeQuery = true)
    int deleteExpiredByTenantId(@Param("tenantId") UUID tenantId,
                                @Param("cutoff") Instant cutoff,
                                @Param("batchSize") int batchSize);

}
