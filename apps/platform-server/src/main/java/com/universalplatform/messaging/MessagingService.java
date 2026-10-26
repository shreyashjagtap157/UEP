package com.universalplatform.messaging;

import com.universalplatform.audit.AuditService;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.MembershipDirectory;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MessagingService {
    private final TenantContext tenantContext;
    private final ActorContext actorContext;
    private final AuthorizationService authorization;
    private final MembershipDirectory memberships;
    private final MessageConversationRepository conversations;
    private final MessageParticipantRepository participants;
    private final MessageRecordRepository messages;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    MessagingService(TenantContext tenantContext, ActorContext actorContext, AuthorizationService authorization,
                     MembershipDirectory memberships, MessageConversationRepository conversations,
                     MessageParticipantRepository participants, MessageRecordRepository messages, AuditService audit) {
        this.tenantContext = tenantContext;
        this.actorContext = actorContext;
        this.authorization = authorization;
        this.memberships = memberships;
        this.conversations = conversations;
        this.participants = participants;
        this.messages = messages;
        this.audit = audit;
    }

    @Transactional
    ConversationView create(String subject, List<UUID> participantMembershipIds) {
        authorization.require(PermissionKey.MESSAGING_SEND);
        UUID tenantId = tenantContext.requireTenantId();
        UUID currentMembershipId = memberships.current().membershipId();
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        ids.add(currentMembershipId);
        if (participantMembershipIds != null) ids.addAll(participantMembershipIds);
        if (ids.size() < 2) throw new MessagingConflictException("A conversation requires at least two active participants");
        if (ids.size() > 50) throw new MessagingConflictException("A conversation may contain at most 50 participants");
        String normalizedSubject = normalizeSubject(subject);
        Instant now = clock.instant();
        UUID conversationId = UUID.randomUUID();
        MessageConversation conversation = conversations.save(new MessageConversation(conversationId, tenantId, normalizedSubject, currentMembershipId, now));
        for (UUID membershipId : ids) {
            memberships.requireActive(membershipId);
            participants.save(new MessageParticipant(conversationId, tenantId, membershipId, now));
        }
        audit.record(tenantId, actorContext.requireSubject(), "MESSAGE_CONVERSATION_CREATED", "message_conversation", conversationId.toString());
        return conversationView(conversation, ids.size());
    }

    @Transactional(readOnly = true)
    List<ConversationView> list(int page, int size) {
        authorization.require(PermissionKey.MESSAGING_VIEW);
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = memberships.current().membershipId();
        List<UUID> ids = participants.findAllByTenantIdAndIdMembershipIdOrderByConversationId(tenantId, membershipId).stream()
                .map(MessageParticipant::conversationId).toList();
        if (ids.isEmpty()) return List.of();
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "updatedAt", "id"));
        return conversations.findDistinctByTenantIdAndIdIn(tenantId, ids, request).stream()
                .map(item -> conversationView(item, participants.findAllByTenantIdAndIdConversationId(tenantId, item.id()).size())).toList();
    }

    @Transactional(readOnly = true)
    MessagePage messages(UUID conversationId, int page, int size) {
        authorization.require(PermissionKey.MESSAGING_VIEW);
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = memberships.current().membershipId();
        requireParticipant(tenantId, conversationId, membershipId);
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<MessageRecord> result = messages.findByTenantIdAndConversationIdOrderByCreatedAtDescIdDesc(tenantId, conversationId, request);
        List<MessageView> views = result.stream().map(MessagingService::messageView).toList();
        return new MessagePage(views, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    MessageView send(UUID conversationId, String body) {
        authorization.require(PermissionKey.MESSAGING_SEND);
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = memberships.current().membershipId();
        MessageConversation conversation = conversation(tenantId, conversationId);
        requireParticipant(tenantId, conversationId, membershipId);
        String normalized = normalizeBody(body);
        Instant now = clock.instant();
        MessageRecord message = messages.save(new MessageRecord(UUID.randomUUID(), conversationId, tenantId, membershipId, normalized, now));
        conversation.touched(now);
        audit.record(tenantId, actorContext.requireSubject(), "MESSAGE_SENT", "message", message.id().toString());
        return messageView(message);
    }

    @Transactional
    void markRead(UUID conversationId) {
        authorization.require(PermissionKey.MESSAGING_VIEW);
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = memberships.current().membershipId();
        MessageParticipant participant = requireParticipant(tenantId, conversationId, membershipId);
        participant.markRead(clock.instant());
    }

    @Transactional
    MessageView edit(UUID messageId, String body, long expectedVersion) {
        authorization.require(PermissionKey.MESSAGING_SEND);
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = memberships.current().membershipId();
        MessageRecord message = message(tenantId, messageId);
        requireParticipant(tenantId, message.conversationId(), membershipId);
        if (!message.senderMembershipId().equals(membershipId)) throw new MessagingConflictException("Only the sender may edit a message");
        if (message.deletedAt() != null) throw new MessagingConflictException("Deleted messages cannot be edited");
        if (message.version() != expectedVersion) throw new MessagingConflictException("Message was modified by another request");
        message.edit(normalizeBody(body), clock.instant());
        return messageView(message);
    }

    @Transactional
    void delete(UUID messageId, long expectedVersion) {
        authorization.require(PermissionKey.MESSAGING_SEND);
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = memberships.current().membershipId();
        MessageRecord message = message(tenantId, messageId);
        requireParticipant(tenantId, message.conversationId(), membershipId);
        if (!message.senderMembershipId().equals(membershipId)) throw new MessagingConflictException("Only the sender may delete a message");
        if (message.version() != expectedVersion) throw new MessagingConflictException("Message was modified by another request");
        if (message.deletedAt() == null) message.delete(clock.instant());
    }

    private MessageConversation conversation(UUID tenantId, UUID id) { return conversations.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new MessagingNotFoundException("Conversation not found")); }
    private MessageRecord message(UUID tenantId, UUID id) { return messages.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new MessagingNotFoundException("Message not found")); }
    private MessageParticipant requireParticipant(UUID tenantId, UUID conversationId, UUID membershipId) {
        return participants.findByTenantIdAndIdConversationIdAndIdMembershipId(tenantId, conversationId, membershipId)
                .orElseThrow(() -> new MessagingNotFoundException("Conversation not found"));
    }
    private static String normalizeSubject(String value) { if (value == null || value.isBlank()) return null; String s = value.trim(); if (s.length() > 240) throw new MessagingConflictException("Conversation subject exceeds 240 characters"); return s; }
    private static String normalizeBody(String value) { if (value == null || value.isBlank()) throw new MessagingConflictException("Message body is required"); String s = value.trim(); if (s.length() > 20000) throw new MessagingConflictException("Message body exceeds 20000 characters"); return s; }
    private static ConversationView conversationView(MessageConversation c, int participantCount) { return new ConversationView(c.id(), c.subject(), c.createdByMembershipId(), c.createdAt(), c.updatedAt(), c.version(), participantCount); }
    private static MessageView messageView(MessageRecord m) { return new MessageView(m.id(), m.conversationId(), m.senderMembershipId(), m.body(), m.createdAt(), m.editedAt(), m.deletedAt(), m.version()); }

    record ConversationView(UUID id, String subject, UUID createdByMembershipId, Instant createdAt, Instant updatedAt, long version, int participantCount) {}
    record MessageView(UUID id, UUID conversationId, UUID senderMembershipId, String body, Instant createdAt, Instant editedAt, Instant deletedAt, long version) {}
    record MessagePage(List<MessageView> items, int page, int size, long totalElements, int totalPages) {}
}
