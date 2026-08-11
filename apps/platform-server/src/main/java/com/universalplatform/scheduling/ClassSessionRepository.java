package com.universalplatform.scheduling;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ClassSessionRepository extends JpaRepository<ClassSession, UUID> {
    Optional<ClassSession> findByTenantIdAndScheduleOccurrenceId(UUID tenantId, UUID scheduleOccurrenceId);
    List<ClassSession> findAllByTenantIdAndScheduleOccurrenceIdIn(UUID tenantId, Collection<UUID> occurrenceIds);
}
