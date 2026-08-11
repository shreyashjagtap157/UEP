package com.universalplatform.academics;

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
@Table(name = "academic_program")
class AcademicProgram {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    private UUID academicPeriodId;
    @Column(nullable = false, length = 64) private String code;
    @Column(nullable = false, length = 200) private String displayName;
    @Column(length = 2000) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ProgramStatus status;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected AcademicProgram() {}

    AcademicProgram(UUID id, UUID tenantId, UUID academicPeriodId, String code, String displayName, String description, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.academicPeriodId = academicPeriodId;
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.status = ProgramStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    UUID academicPeriodId() { return academicPeriodId; }
    String code() { return code; }
    String displayName() { return displayName; }
    String description() { return description; }
    ProgramStatus status() { return status; }
    Instant createdAt() { return createdAt; }
    long version() { return version; }

    void update(UUID academicPeriodId, String code, String displayName, String description, ProgramStatus status, Instant now) {
        this.academicPeriodId = academicPeriodId;
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.status = status;
        this.updatedAt = now;
    }
}
