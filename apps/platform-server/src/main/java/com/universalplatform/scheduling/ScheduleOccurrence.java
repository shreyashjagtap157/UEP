package com.universalplatform.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "schedule_occurrence")
class ScheduleOccurrence {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID seriesId;
    @Column(nullable = false, updatable = false) private Instant originalStartsAt;
    @Column(nullable = false) private Instant startsAt;
    @Column(nullable = false) private Instant endsAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ScheduleOccurrenceStatus status;
    private UUID substituteTeacherMembershipId;
    @Column(length = 120) private String roomCodeOverride;
    @Column(length = 500) private String exceptionReason;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected ScheduleOccurrence() {}

    ScheduleOccurrence(UUID id, UUID tenantId, UUID seriesId, Instant startsAt, Instant endsAt, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.seriesId = seriesId;
        this.originalStartsAt = startsAt;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = ScheduleOccurrenceStatus.SCHEDULED;
        this.updatedAt = now;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    UUID seriesId() { return seriesId; }
    Instant originalStartsAt() { return originalStartsAt; }
    Instant startsAt() { return startsAt; }
    Instant endsAt() { return endsAt; }
    ScheduleOccurrenceStatus status() { return status; }
    UUID substituteTeacherMembershipId() { return substituteTeacherMembershipId; }
    String roomCodeOverride() { return roomCodeOverride; }
    String exceptionReason() { return exceptionReason; }
    long version() { return version; }

    void reschedule(Instant startsAt, Instant endsAt, UUID substituteTeacherMembershipId,
                    String roomCodeOverride, String reason, Instant now) {
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.substituteTeacherMembershipId = substituteTeacherMembershipId;
        this.roomCodeOverride = roomCodeOverride;
        this.exceptionReason = reason;
        this.status = ScheduleOccurrenceStatus.SCHEDULED;
        this.updatedAt = now;
    }

    void cancel(String reason, Instant now) {
        this.status = ScheduleOccurrenceStatus.CANCELLED;
        this.exceptionReason = reason;
        this.updatedAt = now;
    }
}
