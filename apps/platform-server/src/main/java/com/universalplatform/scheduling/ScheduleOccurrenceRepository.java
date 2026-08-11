package com.universalplatform.scheduling;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ScheduleOccurrenceRepository extends JpaRepository<ScheduleOccurrence, UUID> {
    Optional<ScheduleOccurrence> findByTenantIdAndId(UUID tenantId, UUID id);
    List<ScheduleOccurrence> findAllByTenantIdAndSeriesIdOrderByStartsAtAsc(UUID tenantId, UUID seriesId);

    @Query("""
            select o from ScheduleOccurrence o
            where o.tenantId = :tenantId and o.status = com.universalplatform.scheduling.ScheduleOccurrenceStatus.SCHEDULED
              and o.startsAt < :to and o.endsAt > :from
            order by o.startsAt asc, o.id asc
            """)
    List<ScheduleOccurrence> findRange(@Param("tenantId") UUID tenantId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select o from ScheduleOccurrence o, ScheduleSeries s
            where o.tenantId = :tenantId and s.tenantId = :tenantId and o.seriesId = s.id
              and o.status = com.universalplatform.scheduling.ScheduleOccurrenceStatus.SCHEDULED
              and o.startsAt < :to and o.endsAt > :from
              and (s.primaryTeacherMembershipId = :membershipId or o.substituteTeacherMembershipId = :membershipId)
            order by o.startsAt asc, o.id asc
            """)
    List<ScheduleOccurrence> findRangeForTeacher(@Param("tenantId") UUID tenantId, @Param("membershipId") UUID membershipId,
                                                  @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select o from ScheduleOccurrence o, ScheduleSeries s
            where o.tenantId = :tenantId and s.tenantId = :tenantId and o.seriesId = s.id
              and o.status = com.universalplatform.scheduling.ScheduleOccurrenceStatus.SCHEDULED
              and o.startsAt < :to and o.endsAt > :from and s.batchId in :batchIds
            order by o.startsAt asc, o.id asc
            """)
    List<ScheduleOccurrence> findRangeForBatches(@Param("tenantId") UUID tenantId, @Param("batchIds") Collection<UUID> batchIds,
                                                  @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select o from ScheduleOccurrence o, ScheduleSeries s
            where o.tenantId = :tenantId and s.tenantId = :tenantId and o.seriesId = s.id
              and o.status = com.universalplatform.scheduling.ScheduleOccurrenceStatus.SCHEDULED
              and o.startsAt < :to and o.endsAt > :from
              and s.kind in (com.universalplatform.scheduling.ScheduleKind.HOLIDAY, com.universalplatform.scheduling.ScheduleKind.EVENT)
              and (s.branchId is null or s.branchId = :branchId)
            order by o.startsAt asc, o.id asc
            """)
    List<ScheduleOccurrence> findPublicRange(@Param("tenantId") UUID tenantId, @Param("branchId") UUID branchId,
                                              @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select o from ScheduleOccurrence o, ScheduleSeries s
            where o.tenantId = :tenantId and s.tenantId = :tenantId and o.seriesId = s.id
              and o.status = com.universalplatform.scheduling.ScheduleOccurrenceStatus.SCHEDULED
              and o.startsAt < :to and o.endsAt > :from and s.branchId in :branchIds
            order by o.startsAt asc, o.id asc
            """)
    List<ScheduleOccurrence> findRangeForBranches(@Param("tenantId") UUID tenantId, @Param("branchIds") Collection<UUID> branchIds,
                                                   @Param("from") Instant from, @Param("to") Instant to);

    long countByTenantIdAndSeriesIdAndStatus(UUID tenantId, UUID seriesId, ScheduleOccurrenceStatus status);

    void deleteAllByTenantIdAndSeriesId(UUID tenantId, UUID seriesId);
}
