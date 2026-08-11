package com.universalplatform.enrollment;

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
@Table(name = "enrollment")
class Enrollment {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID membershipId;
    private UUID programId;
    private UUID courseId;
    private UUID batchId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private EnrollmentStatus status;
    @Column(nullable = false, updatable = false) private Instant enrolledAt;
    private Instant endedAt;
    @Column(length = 160) private String externalReference;
    @Version private long version;

    protected Enrollment() {}

    Enrollment(UUID id, UUID tenantId, UUID membershipId, UUID programId, UUID courseId, UUID batchId,
               String externalReference, Instant enrolledAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.membershipId = membershipId;
        this.programId = programId;
        this.courseId = courseId;
        this.batchId = batchId;
        this.status = EnrollmentStatus.ENROLLED;
        this.externalReference = externalReference;
        this.enrolledAt = enrolledAt;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    UUID membershipId() { return membershipId; }
    UUID programId() { return programId; }
    UUID courseId() { return courseId; }
    UUID batchId() { return batchId; }
    EnrollmentStatus status() { return status; }
    Instant enrolledAt() { return enrolledAt; }
    Instant endedAt() { return endedAt; }
    String externalReference() { return externalReference; }
    long version() { return version; }

    void changeStatus(EnrollmentStatus status, Instant now) {
        this.status = status;
        this.endedAt = status == EnrollmentStatus.ENROLLED ? null : now;
    }
}
