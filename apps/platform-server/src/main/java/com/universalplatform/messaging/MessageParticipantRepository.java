package com.universalplatform.messaging;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MessageParticipantRepository extends JpaRepository<MessageParticipant, MessageParticipantId> {
    Optional<MessageParticipant> findByTenantIdAndIdConversationIdAndIdMembershipId(UUID tenantId, UUID conversationId, UUID membershipId);
    List<MessageParticipant> findAllByTenantIdAndIdMembershipIdOrderById_ConversationId(UUID tenantId, UUID membershipId);
    List<MessageParticipant> findAllByTenantIdAndIdConversationId(UUID tenantId, UUID conversationId);
}
