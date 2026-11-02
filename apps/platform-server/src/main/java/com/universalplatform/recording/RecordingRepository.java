package com.universalplatform.recording;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RecordingRepository extends JpaRepository<Recording,UUID> {
    Optional<Recording> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<Recording> findByTenantIdAndLiveClassId(UUID tenantId, UUID liveClassId);
    Page<Recording> findAllByTenantId(UUID tenantId, Pageable pageable);
    Page<Recording> findAllByTenantIdAndLiveClassIdIn(UUID tenantId, Collection<UUID> liveClassIds, Pageable pageable);
    List<Recording> findTop100ByStatusInOrderByRequestedAtAsc(Collection<RecordingProcessingStatus> statuses);
    List<Recording> findTop100ByStatusInAndReadyAtBeforeOrderByReadyAtAsc(Collection<RecordingProcessingStatus> statuses, Instant before);
    List<Recording> findTop100ByStatusInOrderByReadyAtAsc(Collection<RecordingProcessingStatus> statuses);
    @Query("""
            select r from Recording r
            where r.status in :statuses
              and (r.requestedAt > :afterRequestedAt
                   or (r.requestedAt = :afterRequestedAt and r.id > :afterId))
            order by r.requestedAt asc, r.id asc
            """)
    List<Recording> findProcessableAfter(
            @Param("statuses") Collection<RecordingProcessingStatus> statuses,
            @Param("afterRequestedAt") Instant afterRequestedAt,
            @Param("afterId") UUID afterId,
            Pageable pageable);

    @Query("""
            select r from Recording r
            where r.status in :statuses
              and r.readyAt is not null
              and r.readyAt < :before
              and (r.readyAt > :afterReadyAt
                   or (r.readyAt = :afterReadyAt and r.id > :afterId))
            order by r.readyAt asc, r.id asc
            """)
    List<Recording> findArchiveCandidatesAfter(
            @Param("statuses") Collection<RecordingProcessingStatus> statuses,
            @Param("before") Instant before,
            @Param("afterReadyAt") Instant afterReadyAt,
            @Param("afterId") UUID afterId,
            Pageable pageable);

    @Query("""
            select r from Recording r
            where r.status in :statuses
              and r.readyAt is not null
              and (r.readyAt > :afterReadyAt
                   or (r.readyAt = :afterReadyAt and r.id > :afterId))
            order by r.readyAt asc, r.id asc
            """)
    List<Recording> findPurgeCandidatesAfter(
            @Param("statuses") Collection<RecordingProcessingStatus> statuses,
            @Param("afterReadyAt") Instant afterReadyAt,
            @Param("afterId") UUID afterId,
            Pageable pageable);
}
