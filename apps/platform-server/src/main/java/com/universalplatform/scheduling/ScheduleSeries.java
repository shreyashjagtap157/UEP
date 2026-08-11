package com.universalplatform.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "schedule_series")
class ScheduleSeries {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ScheduleKind kind;
    @Column(nullable = false, length = 240) private String title;
    @Column(length = 4000) private String description;
    @Column(nullable = false, length = 80) private String timezone;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private DeliveryMode deliveryMode;
    private UUID branchId;
    private UUID batchId;
    private UUID courseId;
    private UUID subjectId;
    private UUID moduleId;
    private UUID primaryTeacherMembershipId;
    @Column(length = 120) private String roomCode;
    @Column(nullable = false) private LocalDateTime startLocal;
    @Column(nullable = false) private int durationMinutes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private RecurrenceFrequency recurrenceFrequency;
    @Column(nullable = false) private int recurrenceInterval;
    @Column(length = 32) private String recurrenceDays;
    private Integer recurrenceDayOfMonth;
    private LocalDateTime recurrenceUntilLocal;
    private Integer recurrenceCount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ScheduleSeriesStatus status;
    @Column(nullable = false, updatable = false, length = 160) private String createdBySubject;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected ScheduleSeries() {}

    ScheduleSeries(UUID id, UUID tenantId, ScheduleKind kind, String title, String description, String timezone,
                   DeliveryMode deliveryMode, UUID branchId, UUID batchId, UUID courseId, UUID subjectId, UUID moduleId,
                   UUID primaryTeacherMembershipId, String roomCode, LocalDateTime startLocal, int durationMinutes,
                   RecurrenceFrequency recurrenceFrequency, int recurrenceInterval, String recurrenceDays,
                   Integer recurrenceDayOfMonth, LocalDateTime recurrenceUntilLocal, Integer recurrenceCount,
                   String createdBySubject, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.kind = kind;
        this.title = title;
        this.description = description;
        this.timezone = timezone;
        this.deliveryMode = deliveryMode;
        this.branchId = branchId;
        this.batchId = batchId;
        this.courseId = courseId;
        this.subjectId = subjectId;
        this.moduleId = moduleId;
        this.primaryTeacherMembershipId = primaryTeacherMembershipId;
        this.roomCode = roomCode;
        this.startLocal = startLocal;
        this.durationMinutes = durationMinutes;
        this.recurrenceFrequency = recurrenceFrequency;
        this.recurrenceInterval = recurrenceInterval;
        this.recurrenceDays = recurrenceDays;
        this.recurrenceDayOfMonth = recurrenceDayOfMonth;
        this.recurrenceUntilLocal = recurrenceUntilLocal;
        this.recurrenceCount = recurrenceCount;
        this.status = ScheduleSeriesStatus.ACTIVE;
        this.createdBySubject = createdBySubject;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    ScheduleKind kind() { return kind; }
    String title() { return title; }
    String description() { return description; }
    String timezone() { return timezone; }
    DeliveryMode deliveryMode() { return deliveryMode; }
    UUID branchId() { return branchId; }
    UUID batchId() { return batchId; }
    UUID courseId() { return courseId; }
    UUID subjectId() { return subjectId; }
    UUID moduleId() { return moduleId; }
    UUID primaryTeacherMembershipId() { return primaryTeacherMembershipId; }
    String roomCode() { return roomCode; }
    LocalDateTime startLocal() { return startLocal; }
    int durationMinutes() { return durationMinutes; }
    RecurrenceFrequency recurrenceFrequency() { return recurrenceFrequency; }
    int recurrenceInterval() { return recurrenceInterval; }
    String recurrenceDays() { return recurrenceDays; }
    Integer recurrenceDayOfMonth() { return recurrenceDayOfMonth; }
    LocalDateTime recurrenceUntilLocal() { return recurrenceUntilLocal; }
    Integer recurrenceCount() { return recurrenceCount; }
    ScheduleSeriesStatus status() { return status; }
    Instant createdAt() { return createdAt; }
    long version() { return version; }

    void cancel(Instant now) {
        this.status = ScheduleSeriesStatus.CANCELLED;
        this.updatedAt = now;
    }
}
