package com.universalplatform.messaging;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MessageDeliveryRepository extends JpaRepository<MessageDelivery, UUID> {
    List<MessageDelivery> findAllByTenantIdAndMessageIdOrderByRecipientMembershipIdAsc(
            UUID tenantId, UUID messageId);

    @Query("""
            select count(d)
            from MessageDelivery d, MessageRecord m
            where d.tenantId = :tenantId
              and d.conversationId = :conversationId
              and d.recipientMembershipId = :membershipId
              and d.status <> com.universalplatform.messaging.MessageDeliveryStatus.READ
              and m.tenantId = d.tenantId
              and m.conversationId = d.conversationId
              and m.id = d.messageId
              and m.deletedAt is null
            """)
    long countUnreadByTenantAndConversationAndRecipient(
            @Param("tenantId") UUID tenantId,
            @Param("conversationId") UUID conversationId,
            @Param("membershipId") UUID membershipId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update message_delivery
            set status = 'DELIVERED',
                delivered_at = :now,
                version = version + 1
            where tenant_id = :tenantId
              and conversation_id = :conversationId
              and recipient_membership_id = :membershipId
              and message_id in (:messageIds)
              and status = 'PENDING'
            """, nativeQuery = true)
    int markDelivered(@Param("tenantId") UUID tenantId,
                      @Param("conversationId") UUID conversationId,
                      @Param("membershipId") UUID membershipId,
                      @Param("messageIds") Collection<UUID> messageIds,
                      @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update message_delivery
            set status = 'READ',
                delivered_at = coalesce(delivered_at, :now),
                read_at = :now,
                version = version + 1
            where tenant_id = :tenantId
              and conversation_id = :conversationId
              and recipient_membership_id = :membershipId
              and status <> 'READ'
            """, nativeQuery = true)
    int markRead(@Param("tenantId") UUID tenantId,
                 @Param("conversationId") UUID conversationId,
                 @Param("membershipId") UUID membershipId,
                 @Param("now") Instant now);
}
