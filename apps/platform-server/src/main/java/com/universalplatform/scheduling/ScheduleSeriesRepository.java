package com.universalplatform.scheduling;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ScheduleSeriesRepository extends JpaRepository<ScheduleSeries, UUID> {
    Optional<ScheduleSeries> findByTenantIdAndId(UUID tenantId, UUID id);
    List<ScheduleSeries> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
    Page<ScheduleSeries> findAllByTenantId(UUID tenantId, Pageable pageable);
    Page<ScheduleSeries> findAllByTenantIdAndBranchIdIn(UUID tenantId, Collection<UUID> branchIds, Pageable pageable);
}
