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
@Table(name = "teacher_assignment")
class TeacherAssignment {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID membershipId;
    private UUID programId;
    private UUID courseId;
    private UUID subjectId;
    private UUID moduleId;
    private UUID batchId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private TeacherAssignmentRole assignmentRole;
    private LocalDate startsOn;
    private LocalDate endsOn;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private TeacherAssignmentStatus status;
    @Column(nullable = false, updatable = false) private Instant assignedAt;
    @Column(nullable = false, length = 160, updatable = false) private String assignedBySubject;
    @Version private long version;

    protected TeacherAssignment() {}

    TeacherAssignment(UUID id, UUID tenantId, UUID membershipId, UUID programId, UUID courseId, UUID subjectId,
                      UUID moduleId, UUID batchId, TeacherAssignmentRole assignmentRole, LocalDate startsOn,
                      String assignedBySubject, Instant assignedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.membershipId = membershipId;
        this.programId = programId;
        this.courseId = courseId;
        this.subjectId = subjectId;
        this.moduleId = moduleId;
        this.batchId = batchId;
        this.assignmentRole = assignmentRole;
        this.startsOn = startsOn;
        this.status = TeacherAssignmentStatus.ACTIVE;
        this.assignedBySubject = assignedBySubject;
        this.assignedAt = assignedAt;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    UUID membershipId() { return membershipId; }
    UUID programId() { return programId; }
    UUID courseId() { return courseId; }
    UUID subjectId() { return subjectId; }
    UUID moduleId() { return moduleId; }
    UUID batchId() { return batchId; }
    TeacherAssignmentRole assignmentRole() { return assignmentRole; }
    LocalDate startsOn() { return startsOn; }
    LocalDate endsOn() { return endsOn; }
    TeacherAssignmentStatus status() { return status; }
    Instant assignedAt() { return assignedAt; }
    long version() { return version; }

    void end(LocalDate endsOn) {
        this.endsOn = endsOn;
        this.status = TeacherAssignmentStatus.ENDED;
    }
}
