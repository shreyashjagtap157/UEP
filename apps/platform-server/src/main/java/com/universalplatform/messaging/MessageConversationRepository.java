package com.universalplatform.messaging;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface MessageConversationRepository extends JpaRepository<MessageConversation, UUID> {
    Optional<MessageConversation> findByTenantIdAndId(UUID tenantId, UUID id);
    Page<MessageConversation> findDistinctByTenantIdAndIdIn(UUID tenantId, java.util.Collection<UUID> ids, Pageable pageable);
}
