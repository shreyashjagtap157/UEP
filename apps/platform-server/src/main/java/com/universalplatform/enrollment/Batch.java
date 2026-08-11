package com.universalplatform.enrollment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "batch")
class Batch {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    private UUID academicPeriodId;
    private UUID programId;
    private UUID courseId;
    private UUID branchId;
    @Column(nullable = false, length = 64) private String code;
    @Column(nullable = false, length = 200) private String displayName;
    @Column(length = 64) private String sectionCode;
    private LocalDate startsOn;
    private LocalDate endsOn;
    private Integer capacity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private BatchStatus status;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected Batch() {}

    Batch(UUID id, UUID tenantId, UUID academicPeriodId, UUID programId, UUID courseId, UUID branchId,
          String code, String displayName, String sectionCode, LocalDate startsOn, LocalDate endsOn,
          Integer capacity, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.academicPeriodId = academicPeriodId;
        this.programId = programId;
        this.courseId = courseId;
        this.branchId = branchId;
        this.code = code;
        this.displayName = displayName;
        this.sectionCode = sectionCode;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.capacity = capacity;
        this.status = BatchStatus.PLANNED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    UUID academicPeriodId() { return academicPeriodId; }
    UUID programId() { return programId; }
    UUID courseId() { return courseId; }
    UUID branchId() { return branchId; }
    String code() { return code; }
    String displayName() { return displayName; }
    String sectionCode() { return sectionCode; }
    LocalDate startsOn() { return startsOn; }
    LocalDate endsOn() { return endsOn; }
    Integer capacity() { return capacity; }
    BatchStatus status() { return status; }
    Instant createdAt() { return createdAt; }
    long version() { return version; }

    void update(UUID academicPeriodId, UUID programId, UUID courseId, UUID branchId, String code,
                String displayName, String sectionCode, LocalDate startsOn, LocalDate endsOn,
                Integer capacity, BatchStatus status, Instant now) {
        this.academicPeriodId = academicPeriodId;
        this.programId = programId;
        this.courseId = courseId;
        this.branchId = branchId;
        this.code = code;
        this.displayName = displayName;
        this.sectionCode = sectionCode;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.capacity = capacity;
        this.status = status;
        this.updatedAt = now;
    }
}
