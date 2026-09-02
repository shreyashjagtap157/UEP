package com.universalplatform.scheduling;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only scheduling boundary used by dashboards and later presence/media modules. */
@Service
public class ScheduleDirectory {
    private final ScheduleService service;
    private final ClassSessionRepository classSessions;
    private final com.universalplatform.security.TenantContext tenantContext;
    private final ScheduleOccurrenceRepository occurrenceRepository;

    ScheduleDirectory(ScheduleService service, ClassSessionRepository classSessions, com.universalplatform.security.TenantContext tenantContext, ScheduleOccurrenceRepository occurrenceRepository) { this.service = service; this.classSessions = classSessions; this.tenantContext = tenantContext; this.occurrenceRepository = occurrenceRepository; }

    @Transactional(readOnly = true)
    public List<ScheduleItem> range(Instant from, Instant to) {
        return service.calendar(from, to).stream().map(item -> new ScheduleItem(item.id(), item.seriesId(), item.classSessionId(),
                item.kind(), item.title(), item.timezone(), item.deliveryMode(), item.branchId(), item.batchId(), item.courseId(),
                item.subjectId(), item.moduleId(), item.effectiveTeacherMembershipId(), item.roomCode(), item.startsAt(), item.endsAt(),
                item.status())).toList();
    }


    @Transactional(readOnly = true)
    public ClassSessionReference requireClassSession(UUID id) {
        ClassSession session = classSessions.findByTenantIdAndId(tenantContext.requireTenantId(), id)
                .orElseThrow(() -> new IllegalArgumentException("Class session not found"));
        ScheduleOccurrence occurrence = occurrenceRepository.findByTenantIdAndId(tenantContext.requireTenantId(), session.scheduleOccurrenceId()).orElse(null);
        return new ClassSessionReference(session.id(), session.scheduleOccurrenceId(), session.batchId(), session.courseId(), session.subjectId(), session.moduleId(), session.lifecycleStatus(),
                occurrence == null ? null : occurrence.startsAt(), occurrence == null ? null : occurrence.endsAt());
    }

    public record ClassSessionReference(UUID id, UUID occurrenceId, UUID batchId, UUID courseId, UUID subjectId, UUID moduleId, String status, Instant startsAt, Instant endsAt) {}

    public record ScheduleItem(UUID occurrenceId, UUID seriesId, UUID classSessionId, ScheduleKind kind, String title,
                               String timezone, DeliveryMode deliveryMode, UUID branchId, UUID batchId, UUID courseId,
                               UUID subjectId, UUID moduleId, UUID effectiveTeacherMembershipId, String roomCode,
                               Instant startsAt, Instant endsAt, ScheduleOccurrenceStatus status) {}
}
