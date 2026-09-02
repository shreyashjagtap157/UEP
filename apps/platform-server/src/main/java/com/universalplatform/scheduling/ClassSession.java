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
@Table(name = "class_session")
class ClassSession {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID scheduleOccurrenceId;
    private UUID batchId;
    private UUID courseId;
    private UUID subjectId;
    private UUID moduleId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private ClassSessionStatus status;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Version private long version;

    protected ClassSession() {}

    ClassSession(UUID id, UUID tenantId, UUID scheduleOccurrenceId, UUID batchId, UUID courseId,
                 UUID subjectId, UUID moduleId, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.scheduleOccurrenceId = scheduleOccurrenceId;
        this.batchId = batchId;
        this.courseId = courseId;
        this.subjectId = subjectId;
        this.moduleId = moduleId;
        this.status = ClassSessionStatus.SCHEDULED;
        this.createdAt = now;
    }

    UUID id() { return id; }
    UUID scheduleOccurrenceId() { return scheduleOccurrenceId; }
    UUID batchId() { return batchId; }
    UUID courseId() { return courseId; }
    UUID subjectId() { return subjectId; }
    UUID moduleId() { return moduleId; }
    String lifecycleStatus() { return status.name(); }
    void cancel() { this.status = ClassSessionStatus.CANCELLED; }
}
