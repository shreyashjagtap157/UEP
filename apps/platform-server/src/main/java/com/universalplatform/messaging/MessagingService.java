package com.universalplatform.messaging;

import com.universalplatform.audit.AuditService;
import com.universalplatform.identity.IdentityAccessDeniedException;
import com.universalplatform.identity.IdentityNotFoundException;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.identity.MembershipDirectory;
import com.universalplatform.notification.NotificationEventType;
import com.universalplatform.notification.NotificationPriority;
import com.universalplatform.notification.NotificationPublisher;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MessagingService {
    private final TenantContext tenantContext;
    private final ActorContext actorContext;
    private final MembershipDirectory memberships;
    private final MessageConversationRepository conversations;
    private final MessageParticipantRepository participants;
    private final MessageBlockRepository blocks;
    private final MessageReportRepository reports;
    private final AuthorizationService authorization;
    private final MessageRecordRepository messages;
    private final AuditService audit;
    private final NotificationPublisher notifications;
    private final Clock clock = Clock.systemUTC();
    MessagingService(TenantContext tenantContext, ActorContext actorContext, MembershipDirectory memberships, MessageConversationRepository conversations,
                     MessageParticipantRepository participants, MessageBlockRepository blocks, MessageRecordRepository messages, MessageReportRepository reports, AuditService audit, NotificationPublisher notifications, AuthorizationService authorization) {
        this.tenantContext=tenantContext; this.actorContext=actorContext; this.memberships=memberships; this.conversations=conversations; this.participants=participants; this.blocks=blocks; this.messages=messages; this.reports=reports; this.audit=audit; this.notifications=notifications; this.authorization=authorization;
    }
    @Transactional
    ConversationView create(String subject, List<UUID> participantMembershipIds) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID currentMembershipId = currentMembershipId();
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        ids.add(currentMembershipId);
        if (participantMembershipIds != null) ids.addAll(participantMembershipIds);
        if (ids.size() < 2) throw new MessagingConflictException("A conversation requires at least two active participants");
        if (ids.size() > 50) throw new MessagingConflictException("A conversation may contain at most 50 participants");

        for (UUID membershipId : ids) requireActiveParticipant(membershipId);
        if (blocks.existsBlockBetweenParticipants(tenantId, ids)) {
            throw new MessagingConflictException("A conversation cannot include participants who have blocked each other");
        }

        String normalizedSubject = normalizeSubject(subject);
        Instant now = clock.instant();
        UUID conversationId = UUID.randomUUID();
        MessageConversation conversation = conversations.save(
                new MessageConversation(conversationId, tenantId, normalizedSubject, currentMembershipId, now));
        for (UUID membershipId : ids) {
            participants.save(new MessageParticipant(conversationId, tenantId, membershipId, now));
        }
        audit.record(tenantId, actorContext.requireSubject(), "MESSAGE_CONVERSATION_CREATED",
                "message_conversation", conversationId.toString());
        return conversationView(conversation, ids.size(), 0);
    }

    @Transactional(readOnly=true)
    List<ConversationView> list(int page,int size) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = currentMembershipId();
        List<MessageParticipant> myParticipants =
                participants.findAllByTenantIdAndIdMembershipIdOrderById_ConversationId(tenantId, membershipId);
        if (myParticipants.isEmpty()) return List.of();

        Map<UUID, MessageParticipant> currentParticipantByConversation = myParticipants.stream()
                .collect(Collectors.toMap(MessageParticipant::conversationId, participant -> participant));
        List<UUID> ids = myParticipants.stream().map(MessageParticipant::conversationId).toList();
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt", "id"));
        return conversations.findDistinctByTenantIdAndIdIn(tenantId, ids, request).stream()
                .map(item -> {
                    MessageParticipant currentParticipant = currentParticipantByConversation.get(item.id());
                    long unreadCount = currentParticipant.lastReadAt() == null
                            ? messages.countByTenantIdAndConversationIdAndSenderMembershipIdNotAndDeletedAtIsNull(
                                    tenantId, item.id(), membershipId)
                            : messages.countByTenantIdAndConversationIdAndSenderMembershipIdNotAndDeletedAtIsNullAndCreatedAtAfter(
                                    tenantId, item.id(), membershipId, currentParticipant.lastReadAt());
                    int participantCount = participants.findAllByTenantIdAndIdConversationId(tenantId, item.id()).size();
                    return conversationView(item, participantCount, unreadCount);
                })
                .toList();
    }

    @Transactional(readOnly=true) MessagePage messages(UUID conversationId,int page,int size){
        UUID tenantId=tenantContext.requireTenantId(); UUID membershipId=currentMembershipId(); requireParticipant(tenantId,conversationId,membershipId); PageRequest request=PageRequest.of(Math.max(page,0),Math.min(Math.max(size,1),100),Sort.by(Sort.Direction.DESC,"createdAt","id")); Page<MessageRecord> result=messages.findByTenantIdAndConversationIdOrderByCreatedAtDescIdDesc(tenantId,conversationId,request); return new MessagePage(result.stream().map(MessagingService::messageView).toList(),result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages());
    }
    @Transactional
    MessageView send(UUID conversationId, String body) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = currentMembershipId();
        MessageConversation conversation = conversation(tenantId, conversationId);
        requireParticipant(tenantId, conversationId, membershipId);
        List<MessageParticipant> conversationParticipants =
                participants.findAllByTenantIdAndIdConversationId(tenantId, conversationId);
        if (conversationParticipants.size() == 2) {
            UUID otherMembershipId = conversationParticipants.stream()
                    .map(MessageParticipant::membershipId)
                    .filter(id -> !id.equals(membershipId))
                    .findFirst()
                    .orElse(null);
            if (otherMembershipId != null && blocks.existsBetween(tenantId, membershipId, otherMembershipId)) {
                throw new MessagingConflictException("Messaging is blocked between these participants");
            }
        }
        String normalized = normalizeBody(body);
        Instant now = clock.instant();
        MessageRecord message = messages.save(
                new MessageRecord(UUID.randomUUID(), conversationId, tenantId, membershipId, normalized, now));
        conversation.touched(now);
        enqueueMessageNotification(tenantId, conversation, message, membershipId, conversationParticipants);
        audit.record(tenantId, actorContext.requireSubject(), "MESSAGE_SENT", "message", message.id().toString());
        return messageView(message);
    }

    @Transactional void markRead(UUID conversationId){UUID tenantId=tenantContext.requireTenantId();UUID membershipId=currentMembershipId();requireParticipant(tenantId,conversationId,membershipId).markRead(clock.instant());}
    @Transactional MessageView edit(UUID messageId,String body,long expectedVersion){UUID tenantId=tenantContext.requireTenantId();UUID membershipId=currentMembershipId();MessageRecord message=message(tenantId,messageId);requireParticipant(tenantId,message.conversationId(),membershipId);if(!message.senderMembershipId().equals(membershipId))throw new MessagingConflictException("Only the sender may edit a message");if(message.deletedAt()!=null)throw new MessagingConflictException("Deleted messages cannot be edited");if(message.version()!=expectedVersion)throw new MessagingConflictException("Message was modified by another request");Instant now=clock.instant();message.edit(normalizeBody(body),now);audit.record(tenantId,actorContext.requireSubject(),"MESSAGE_EDITED","message",message.id().toString());return messageView(message);}
    @Transactional void delete(UUID messageId,long expectedVersion){UUID tenantId=tenantContext.requireTenantId();UUID membershipId=currentMembershipId();MessageRecord message=message(tenantId,messageId);requireParticipant(tenantId,message.conversationId(),membershipId);if(!message.senderMembershipId().equals(membershipId))throw new MessagingConflictException("Only the sender may delete a message");if(message.version()!=expectedVersion)throw new MessagingConflictException("Message was modified by another request");if(message.deletedAt()==null){Instant now=clock.instant();message.delete(now);audit.record(tenantId,actorContext.requireSubject(),"MESSAGE_DELETED","message",message.id().toString());}}
    @Transactional
    MessageReportView reportMessage(UUID messageId, String reason) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID reporterMembershipId = currentMembershipId();
        MessageRecord target = message(tenantId, messageId);
        requireParticipant(tenantId, target.conversationId(), reporterMembershipId);
        if (target.senderMembershipId().equals(reporterMembershipId)) {
            throw new MessagingConflictException("A participant cannot report their own message");
        }
        if (reports.existsByTenantIdAndMessageIdAndReporterMembershipId(tenantId, messageId, reporterMembershipId)) {
            throw new MessagingConflictException("This membership has already reported the message");
        }
        String normalizedReason = normalizeRequired(reason, "Report reason", 2000);
        MessageReport report = reports.save(new MessageReport(
                UUID.randomUUID(), tenantId, target.conversationId(), messageId,
                reporterMembershipId, normalizedReason, clock.instant()));
        audit.record(tenantId, actorContext.requireSubject(), "MESSAGE_REPORTED", "message_report", report.id().toString());
        return reportView(report);
    }

    @Transactional(readOnly=true)
    MessageReportPage listReports(int page, int size) {
        requireModerator();
        UUID tenantId = tenantContext.requireTenantId();
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<MessageReport> result = reports.findAllByTenantId(tenantId, request);
        return new MessageReportPage(result.getContent().stream().map(MessagingService::reportView).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    MessageReportView resolveReport(UUID reportId, MessageReportStatus status, String resolution, long expectedVersion) {
        requireModerator();
        if (status == null || status == MessageReportStatus.OPEN) {
            throw new MessagingConflictException("A report must be resolved to REVIEWED, DISMISSED, or ACTIONED");
        }
        String normalizedResolution = normalizeRequired(resolution, "Resolution", 2000);
        UUID tenantId = tenantContext.requireTenantId();
        UUID reviewerMembershipId = currentMembershipId();
        MessageReport report = reports.findByTenantIdAndId(tenantId, reportId)
                .orElseThrow(() -> new MessagingNotFoundException("Report not found"));
        if (report.status() != MessageReportStatus.OPEN) throw new MessagingConflictException("Report has already been resolved");
        if (report.version() != expectedVersion) throw new MessagingConflictException("Report was modified by another request");
        MessageRecord target = message(tenantId, report.messageId());
        if (target.senderMembershipId().equals(reviewerMembershipId)) {
            throw new MessagingConflictException("A message sender cannot review a report on their own message");
        }

        Instant now = clock.instant();
        report.resolve(status, reviewerMembershipId, normalizedResolution, now);
        if (status == MessageReportStatus.ACTIONED && target.deletedAt() == null) {
            target.moderateDelete(now);
            audit.record(tenantId, actorContext.requireSubject(), "MESSAGE_MODERATION_ACTIONED", "message", target.id().toString());
        }
        audit.record(tenantId, actorContext.requireSubject(), "MESSAGE_REPORT_RESOLVED", "message_report", report.id().toString());
        return reportView(report);
    }

    @Transactional(readOnly=true)
    List<BlockView> blockedMemberships() {
        UUID tenantId = tenantContext.requireTenantId();
        UUID membershipId = currentMembershipId();
        return blocks.findAllByTenantIdAndBlockerMembershipIdOrderByCreatedAtDesc(tenantId, membershipId)
                .stream()
                .map(block -> new BlockView(block.blockedMembershipId(), block.createdAt()))
                .toList();
    }

    @Transactional
    void blockMembership(UUID blockedMembershipId) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID blockerMembershipId = currentMembershipId();
        if (blockerMembershipId.equals(blockedMembershipId)) {
            throw new MessagingConflictException("A membership cannot block itself");
        }
        requireActiveParticipant(blockedMembershipId);
        if (blocks.findByTenantIdAndBlockerMembershipIdAndBlockedMembershipId(
                tenantId, blockerMembershipId, blockedMembershipId).isPresent()) return;
        MessageBlock block = blocks.save(new MessageBlock(
                UUID.randomUUID(), tenantId, blockerMembershipId, blockedMembershipId, clock.instant()));
        audit.record(tenantId, actorContext.requireSubject(), "MESSAGE_MEMBER_BLOCKED", "message_block", block.id().toString());
    }

    @Transactional
    void unblockMembership(UUID blockedMembershipId) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID blockerMembershipId = currentMembershipId();
        blocks.findByTenantIdAndBlockerMembershipIdAndBlockedMembershipId(
                tenantId, blockerMembershipId, blockedMembershipId).ifPresent(block -> {
                    blocks.delete(block);
                    audit.record(tenantId, actorContext.requireSubject(), "MESSAGE_MEMBER_UNBLOCKED",
                            "message_block", block.id().toString());
                });
    }

    private MessageConversation conversation(UUID tenantId,UUID id){return conversations.findByTenantIdAndId(tenantId,id).orElseThrow(()->new MessagingNotFoundException("Conversation not found"));}
    private MessageRecord message(UUID tenantId,UUID id){return messages.findByTenantIdAndId(tenantId,id).orElseThrow(()->new MessagingNotFoundException("Message not found"));}
    private MessageParticipant requireParticipant(UUID tenantId,UUID conversationId,UUID membershipId){return participants.findByTenantIdAndIdConversationIdAndIdMembershipId(tenantId,conversationId,membershipId).orElseThrow(()->new MessagingNotFoundException("Conversation not found"));}
    private void enqueueMessageNotification(UUID tenantId, MessageConversation conversation,
                                             MessageRecord message, UUID senderMembershipId,
                                             List<MessageParticipant> conversationParticipants) {
        List<UUID> recipientIds = conversationParticipants.stream()
                .map(MessageParticipant::membershipId)
                .filter(id -> !id.equals(senderMembershipId))
                .toList();
        List<NotificationPublisher.Recipient> recipients = memberships.activeReferences(recipientIds).values().stream()
                .filter(member -> !blocks.existsBetween(tenantId, senderMembershipId, member.membershipId()))
                .map(member -> new NotificationPublisher.Recipient(member.membershipId(), member.email()))
                .toList();
        String title = conversation.subject() == null || conversation.subject().isBlank()
                ? "New message"
                : "New message: " + conversation.subject();
        if (title.length() > 240) title = title.substring(0, 240);
        notifications.enqueue(new NotificationPublisher.NotificationRequest(
                tenantId, NotificationEventType.MESSAGE_RECEIVED, "message", message.id(), title,
                "A participant sent you a message. Sign in to UEP to read it.", NotificationPriority.NORMAL,
                "conversation", conversation.id(), "MESSAGE_RECEIVED:" + message.id(), null, recipients));
    }

    private UUID currentMembershipId() {
        try {
            return memberships.current().membershipId();
        } catch (IdentityAccessDeniedException | IdentityNotFoundException exception) {
            throw new MessagingAccessDeniedException();
        }
    }

    private void requireActiveParticipant(UUID membershipId) {
        try {
            memberships.requireActive(membershipId);
        } catch (IdentityNotFoundException exception) {
            throw new MessagingParticipantNotFoundException();
        } catch (IdentityAccessDeniedException exception) {
            throw new MessagingAccessDeniedException();
        }
    }

    private void requireModerator() {
        try {
            authorization.require(PermissionKey.MESSAGING_MODERATE);
        } catch (IdentityAccessDeniedException exception) {
            throw new MessagingAccessDeniedException();
        }
    }

    private static String normalizeRequired(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new MessagingConflictException(field + " is required");
        String normalized = value.trim();
        if (normalized.length() > max) throw new MessagingConflictException(field + " exceeds " + max + " characters");
        return normalized;
    }

    private static MessageReportView reportView(MessageReport report) {
        return new MessageReportView(report.id(), report.messageId(), report.conversationId(),
                report.reporterMembershipId(), report.reason(), report.status(), report.createdAt(),
                report.reviewedAt(), report.reviewedByMembershipId(), report.resolution(), report.version());
    }

    private static String normalizeSubject(String value){if(value==null||value.isBlank())return null;String s=value.trim();if(s.length()>240)throw new MessagingConflictException("Conversation subject exceeds 240 characters");return s;}
    private static String normalizeBody(String value){if(value==null||value.isBlank())throw new MessagingConflictException("Message body is required");String s=value.trim();if(s.length()>20000)throw new MessagingConflictException("Message body exceeds 20000 characters");return s;}
    private static ConversationView conversationView(MessageConversation c,int participantCount,long unreadCount){return new ConversationView(c.id(),c.subject(),c.createdByMembershipId(),c.createdAt(),c.updatedAt(),c.version(),participantCount,unreadCount);}
    private static MessageView messageView(MessageRecord m){return new MessageView(m.id(),m.conversationId(),m.senderMembershipId(),m.deletedAt()!=null?"[deleted]":m.body(),m.createdAt(),m.editedAt(),m.deletedAt(),m.version());}
    record ConversationView(UUID id,String subject,UUID createdByMembershipId,Instant createdAt,Instant updatedAt,long version,int participantCount,long unreadCount){}
    record MessageView(UUID id,UUID conversationId,UUID senderMembershipId,String body,Instant createdAt,Instant editedAt,Instant deletedAt,long version){}
    record MessagePage(List<MessageView> items,int page,int size,long totalElements,int totalPages){}
    record BlockView(UUID membershipId, Instant createdAt){}
    record MessageReportView(UUID id, UUID messageId, UUID conversationId, UUID reporterMembershipId,
                             String reason, MessageReportStatus status, Instant createdAt, Instant reviewedAt,
                             UUID reviewedByMembershipId, String resolution, long version) {}
    record MessageReportPage(List<MessageReportView> items, int page, int size, long totalElements, int totalPages) {}

}
