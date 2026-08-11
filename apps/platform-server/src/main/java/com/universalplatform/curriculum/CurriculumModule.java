package com.universalplatform.curriculum;

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
@Table(name = "curriculum_module")
class CurriculumModule {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    private UUID programId;
    private UUID courseId;
    private UUID subjectId;
    @Column(nullable = false, length = 64) private String code;
    @Column(nullable = false, length = 200) private String displayName;
    @Column(length = 4000) private String description;
    private Integer sequenceNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private CurriculumStatus status;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected CurriculumModule() {}

    CurriculumModule(UUID id, UUID tenantId, UUID programId, UUID courseId, UUID subjectId, String code,
                     String displayName, String description, Integer sequenceNumber, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.programId = programId;
        this.courseId = courseId;
        this.subjectId = subjectId;
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.sequenceNumber = sequenceNumber;
        this.status = CurriculumStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    UUID programId() { return programId; }
    UUID courseId() { return courseId; }
    UUID subjectId() { return subjectId; }
    String code() { return code; }
    String displayName() { return displayName; }
    String description() { return description; }
    Integer sequenceNumber() { return sequenceNumber; }
    CurriculumStatus status() { return status; }
    Instant createdAt() { return createdAt; }
    long version() { return version; }

    void update(UUID programId, UUID courseId, UUID subjectId, String code, String displayName, String description,
                Integer sequenceNumber, CurriculumStatus status, Instant now) {
        this.programId = programId;
        this.courseId = courseId;
        this.subjectId = subjectId;
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.sequenceNumber = sequenceNumber;
        this.status = status;
        this.updatedAt = now;
    }
}
