package com.universalplatform.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_report", uniqueConstraints = @UniqueConstraint(
        name = "uq_message_reporter_message",
        columnNames = {"tenant_id", "message_id", "reporter_membership_id"}))
class MessageReport {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID conversationId;
    @Column(nullable = false) private UUID messageId;
    @Column(nullable = false) private UUID reporterMembershipId;
    @Column(nullable = false) private UUID messageSenderMembershipId;
    @Column(nullable = false, columnDefinition = "text") private String reportedMessageBody;
    @Column(nullable = false) private Instant messageCreatedAt;
    @Column(nullable = false, length = 2000) private String reason;
    @Column(nullable = false, length = 24) private String status;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    private Instant reviewedAt;
    private UUID reviewedByMembershipId;
    @Column(length = 2000) private String resolution;
    @Version private long version;

    protected MessageReport() {}

    MessageReport(UUID id, UUID tenantId, UUID conversationId, UUID messageId,
                  UUID reporterMembershipId, UUID messageSenderMembershipId, String reportedMessageBody,
                  Instant messageCreatedAt, String reason, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.reporterMembershipId = reporterMembershipId;
        this.messageSenderMembershipId = messageSenderMembershipId;
        this.reportedMessageBody = reportedMessageBody;
        this.messageCreatedAt = messageCreatedAt;
        this.reason = reason;
        this.status = MessageReportStatus.OPEN.name();
        this.createdAt = createdAt;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    UUID conversationId() { return conversationId; }
    UUID messageId() { return messageId; }
    UUID reporterMembershipId() { return reporterMembershipId; }
    UUID messageSenderMembershipId() { return messageSenderMembershipId; }
    String reportedMessageBody() { return reportedMessageBody; }
    Instant messageCreatedAt() { return messageCreatedAt; }
    String reason() { return reason; }
    MessageReportStatus status() { return MessageReportStatus.valueOf(status); }
    Instant createdAt() { return createdAt; }
    Instant reviewedAt() { return reviewedAt; }
    UUID reviewedByMembershipId() { return reviewedByMembershipId; }
    String resolution() { return resolution; }
    long version() { return version; }

    void resolve(MessageReportStatus status, UUID reviewerMembershipId, String resolution, Instant now) {
        if (status == null || status == MessageReportStatus.OPEN) {
            throw new MessagingConflictException("A report must be resolved to a terminal status");
        }
        this.status = status.name();
        this.reviewedByMembershipId = reviewerMembershipId;
        this.reviewedAt = now;
        this.resolution = resolution;
    }
}
