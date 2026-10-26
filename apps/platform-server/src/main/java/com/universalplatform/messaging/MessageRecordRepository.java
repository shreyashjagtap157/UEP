package com.universalplatform.messaging;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MessageRecordRepository extends JpaRepository<MessageRecord, UUID> {
    Page<MessageRecord> findByTenantIdAndConversationIdOrderByCreatedAtDescIdDesc(UUID tenantId, UUID conversationId, Pageable pageable);
    Optional<MessageRecord> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("""
            select count(m)
            from MessageRecord m
            where m.tenantId = :tenantId
              and m.conversationId = :conversationId
              and m.senderMembershipId <> :membershipId
              and (:lastReadAt is null or m.createdAt > :lastReadAt)
            """)
    long countUnreadByTenantAndConversation(
            @Param("tenantId") UUID tenantId,
            @Param("conversationId") UUID conversationId,
            @Param("membershipId") UUID membershipId,
            @Param("lastReadAt") Instant lastReadAt);
}
