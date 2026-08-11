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
@Table(name = "subject")
class Subject {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    private UUID courseId;
    @Column(nullable = false, length = 64) private String code;
    @Column(nullable = false, length = 200) private String displayName;
    @Column(length = 4000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private CurriculumStatus status;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected Subject() {}

    Subject(UUID id, UUID tenantId, UUID courseId, String code, String displayName, String description, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.courseId = courseId;
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.status = CurriculumStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    UUID courseId() { return courseId; }
    String code() { return code; }
    String displayName() { return displayName; }
    String description() { return description; }
    CurriculumStatus status() { return status; }
    Instant createdAt() { return createdAt; }
    long version() { return version; }

    void update(UUID courseId, String code, String displayName, String description, CurriculumStatus status, Instant now) {
        this.courseId = courseId;
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.status = status;
        this.updatedAt = now;
    }
}
