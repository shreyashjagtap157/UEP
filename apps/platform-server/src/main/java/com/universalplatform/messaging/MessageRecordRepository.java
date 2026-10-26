package com.universalplatform.messaging;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface MessageRecordRepository extends JpaRepository<MessageRecord, UUID> {
    Page<MessageRecord> findByTenantIdAndConversationIdOrderByCreatedAtDescIdDesc(UUID tenantId, UUID conversationId, Pageable pageable);
    Optional<MessageRecord> findByTenantIdAndId(UUID tenantId, UUID id);

    long countByTenantIdAndConversationIdAndSenderMembershipIdNotAndDeletedAtIsNull(
            UUID tenantId, UUID conversationId, UUID senderMembershipId);

    long countByTenantIdAndConversationIdAndSenderMembershipIdNotAndDeletedAtIsNullAndCreatedAtAfter(
            UUID tenantId, UUID conversationId, UUID senderMembershipId, Instant createdAt);
}
