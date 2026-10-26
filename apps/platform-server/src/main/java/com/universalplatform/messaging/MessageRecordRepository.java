package com.universalplatform.messaging;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

interface MessageRecordRepository extends JpaRepository<MessageRecord, UUID> {
    Page<MessageRecord> findByTenantIdAndConversationIdOrderByCreatedAtDescIdDesc(UUID tenantId, UUID conversationId, Pageable pageable);
    Optional<MessageRecord> findByTenantIdAndId(UUID tenantId, UUID id);

    @Modifying
    @Query(value = """
            insert into message_rate_window (
                tenant_id, membership_id, window_type, window_start, message_count, report_count
            )
            values (:tenantId, :membershipId, 'MINUTE', date_trunc('minute', current_timestamp), 1, 0)
            on conflict (tenant_id, membership_id, window_type, window_start)
            do update set message_count = message_rate_window.message_count + 1
            where message_rate_window.message_count < :maxCount
            """, nativeQuery = true)
    int consumeMessagePermit(@Param("tenantId") UUID tenantId,
                             @Param("membershipId") UUID membershipId,
                             @Param("maxCount") int maxCount);

    @Modifying
    @Query(value = """
            delete from message_record m
            where m.id in (
                select m2.id from message_record m2
                where m2.tenant_id = :tenantId
                  and m2.created_at < :cutoff
                  and not exists (
                      select 1 from message_report r
                      where r.tenant_id = m2.tenant_id and r.message_id = m2.id
                  )
                order by m2.created_at, m2.id
                limit :batchSize
            )
            """, nativeQuery = true)
    int deleteExpiredUnreported(@Param("tenantId") UUID tenantId,
                                @Param("cutoff") Instant cutoff,
                                @Param("batchSize") int batchSize);


    long countByTenantIdAndConversationIdAndSenderMembershipIdNotAndDeletedAtIsNull(
            UUID tenantId, UUID conversationId, UUID senderMembershipId);

    long countByTenantIdAndConversationIdAndSenderMembershipIdNotAndDeletedAtIsNullAndCreatedAtAfter(
            UUID tenantId, UUID conversationId, UUID senderMembershipId, Instant createdAt);
}
