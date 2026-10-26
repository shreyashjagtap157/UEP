package com.universalplatform.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "message_tenant_policy")
class MessageTenantPolicy {
    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false) private boolean messagingEnabled;
    @Column(nullable = false) private boolean allowStudentToStudent;
    @Column(nullable = false) private int messageRetentionDays;
    @Column(nullable = false) private int reportRetentionDays;
    @Column(nullable = false) private int maxMessagesPerMinute;
    @Column(nullable = false) private int maxReportsPerHour;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected MessageTenantPolicy() {}

    MessageTenantPolicy(UUID tenantId, Instant now) {
        this.tenantId = tenantId;
        this.messagingEnabled = true;
        this.allowStudentToStudent = true;
        this.messageRetentionDays = 365;
        this.reportRetentionDays = 1095;
        this.maxMessagesPerMinute = 30;
        this.maxReportsPerHour = 5;
        this.updatedAt = now;
    }

    UUID tenantId() { return tenantId; }
    boolean messagingEnabled() { return messagingEnabled; }
    boolean allowStudentToStudent() { return allowStudentToStudent; }
    int messageRetentionDays() { return messageRetentionDays; }
    int reportRetentionDays() { return reportRetentionDays; }
    int maxMessagesPerMinute() { return maxMessagesPerMinute; }
    int maxReportsPerHour() { return maxReportsPerHour; }
    Instant updatedAt() { return updatedAt; }
    long version() { return version; }

    void update(boolean messagingEnabled, boolean allowStudentToStudent, int messageRetentionDays,
                int reportRetentionDays, int maxMessagesPerMinute, int maxReportsPerHour, Instant now) {
        if (messageRetentionDays < 1 || messageRetentionDays > 3650) {
            throw new MessagingConflictException("Message retention must be between 1 and 3650 days");
        }
        if (reportRetentionDays < messageRetentionDays || reportRetentionDays > 7300) {
            throw new MessagingConflictException("Report retention must be at least message retention and at most 7300 days");
        }
        if (maxMessagesPerMinute < 1 || maxMessagesPerMinute > 120) {
            throw new MessagingConflictException("Message rate limit must be between 1 and 120 per minute");
        }
        if (maxReportsPerHour < 1 || maxReportsPerHour > 20) {
            throw new MessagingConflictException("Report rate limit must be between 1 and 20 per hour");
        }
        this.messagingEnabled = messagingEnabled;
        this.allowStudentToStudent = allowStudentToStudent;
        this.messageRetentionDays = messageRetentionDays;
        this.reportRetentionDays = reportRetentionDays;
        this.maxMessagesPerMinute = maxMessagesPerMinute;
        this.maxReportsPerHour = maxReportsPerHour;
        this.updatedAt = now;
    }
}
