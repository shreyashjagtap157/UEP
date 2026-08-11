package com.universalplatform.communication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "announcement_target")
class AnnouncementTarget {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID announcementId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private AnnouncementTargetKind targetKind;
    private UUID branchId;
    private UUID courseId;
    private UUID batchId;
    private UUID subjectId;
    private UUID roleId;
    private UUID membershipId;

    protected AnnouncementTarget() {}

    AnnouncementTarget(UUID id, UUID tenantId, UUID announcementId, Target target) {
        this.id = id;
        this.tenantId = tenantId;
        this.announcementId = announcementId;
        this.targetKind = target.kind();
        switch (target.kind()) {
            case ORGANIZATION -> { }
            case BRANCH -> this.branchId = target.id();
            case COURSE -> this.courseId = target.id();
            case BATCH -> this.batchId = target.id();
            case SUBJECT -> this.subjectId = target.id();
            case ROLE -> this.roleId = target.id();
            case MEMBERSHIP -> this.membershipId = target.id();
        }
    }

    AnnouncementTargetKind targetKind() { return targetKind; }
    UUID targetId() {
        return switch (targetKind) {
            case ORGANIZATION -> null;
            case BRANCH -> branchId;
            case COURSE -> courseId;
            case BATCH -> batchId;
            case SUBJECT -> subjectId;
            case ROLE -> roleId;
            case MEMBERSHIP -> membershipId;
        };
    }

    record Target(AnnouncementTargetKind kind, UUID id) {}
}
