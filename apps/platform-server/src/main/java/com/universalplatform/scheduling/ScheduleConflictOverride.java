package com.universalplatform.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "schedule_conflict_override")
class ScheduleConflictOverride {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    private UUID seriesId;
    private UUID occurrenceId;
    @Column(nullable = false, length = 500) private String reason;
    @Column(nullable = false, columnDefinition = "text") private String conflictSummary;
    @Column(nullable = false, length = 160) private String actorSubject;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    protected ScheduleConflictOverride() {}

    ScheduleConflictOverride(UUID id, UUID tenantId, UUID seriesId, UUID occurrenceId, String reason,
                             String conflictSummary, String actorSubject, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.seriesId = seriesId;
        this.occurrenceId = occurrenceId;
        this.reason = reason;
        this.conflictSummary = conflictSummary;
        this.actorSubject = actorSubject;
        this.createdAt = createdAt;
    }
}
