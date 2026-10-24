package com.universalplatform.scheduling;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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

    /** Same overlap query as {@link #findRange} but bounded in SQL via the supplied pageable (LIMIT). */
    @Query("""
            select o from ScheduleOccurrence o
            where o.tenantId = :tenantId and o.status = com.universalplatform.scheduling.ScheduleOccurrenceStatus.SCHEDULED
              and o.startsAt < :to and o.endsAt > :from
            order by o.startsAt asc, o.id asc
            """)
    List<ScheduleOccurrence> findRangeCapped(@Param("tenantId") UUID tenantId, @Param("from") Instant from,
            @Param("to") Instant to, Pageable pageable);

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

    /** Capped variant of {@link #findRangeForTeacher}. */
    @Query("""
            select o from ScheduleOccurrence o, ScheduleSeries s
            where o.tenantId = :tenantId and s.tenantId = :tenantId and o.seriesId = s.id
              and o.status = com.universalplatform.scheduling.ScheduleOccurrenceStatus.SCHEDULED
              and o.startsAt < :to and o.endsAt > :from
              and (s.primaryTeacherMembershipId = :membershipId or o.substituteTeacherMembershipId = :membershipId)
            order by o.startsAt asc, o.id asc
            """)
    List<ScheduleOccurrence> findRangeForTeacherCapped(@Param("tenantId") UUID tenantId,
            @Param("membershipId") UUID membershipId, @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("""
            select o from ScheduleOccurrence o, ScheduleSeries s
            where o.tenantId = :tenantId and s.tenantId = :tenantId and o.seriesId = s.id
              and o.status = com.universalplatform.scheduling.ScheduleOccurrenceStatus.SCHEDULED
              and o.startsAt < :to and o.endsAt > :from and s.batchId in :batchIds
            order by o.startsAt asc, o.id asc
            """)
    List<ScheduleOccurrence> findRangeForBatches(@Param("tenantId") UUID tenantId, @Param("batchIds") Collection<UUID> batchIds,
                                                  @Param("from") Instant from, @Param("to") Instant to);

    /** Capped variant of {@link #findRangeForBatches}. */
    @Query("""
            select o from ScheduleOccurrence o, ScheduleSeries s
            where o.tenantId = :tenantId and s.tenantId = :tenantId and o.seriesId = s.id
              and o.status = com.universalplatform.scheduling.ScheduleOccurrenceStatus.SCHEDULED
              and o.startsAt < :to and o.endsAt > :from and s.batchId in :batchIds
            order by o.startsAt asc, o.id asc
            """)
    List<ScheduleOccurrence> findRangeForBatchesCapped(@Param("tenantId") UUID tenantId,
            @Param("batchIds") Collection<UUID> batchIds, @Param("from") Instant from, @Param("to") Instant to,
            Pageable pageable);

    @Query("""
            select o from ScheduleOccurrence o, ScheduleSeries s
            where o.tenantId = :tenantId and s.tenantId = :tenantId and o.seriesId = s.id
              and o.status = com.universalplatform.scheduling.ScheduleOccurrenceStatus.SCHEDULED
              and o.startsAt < :to and o.endsAt > :from and s.courseId in :courseIds
            order by o.startsAt asc, o.id asc
            """)
    List<ScheduleOccurrence> findRangeForCourses(@Param("tenantId") UUID tenantId, @Param("courseIds") Collection<UUID> courseIds,
                                                  @Param("from") Instant from, @Param("to") Instant to);

    /** Capped variant of {@link #findRangeForCourses}. */
    @Query("""
            select o from ScheduleOccurrence o, ScheduleSeries s
            where o.tenantId = :tenantId and s.tenantId = :tenantId and o.seriesId = s.id
              and o.status = com.universalplatform.scheduling.ScheduleOccurrenceStatus.SCHEDULED
              and o.startsAt < :to and o.endsAt > :from and s.courseId in :courseIds
            order by o.startsAt asc, o.id asc
            """)
    List<ScheduleOccurrence> findRangeForCoursesCapped(@Param("tenantId") UUID tenantId,
            @Param("courseIds") Collection<UUID> courseIds, @Param("from") Instant from, @Param("to") Instant to,
            Pageable pageable);

    @Query("""
            select o from ScheduleOccurrence o, ScheduleSeries s
            where o.tenantId = :tenantId and s.tenantId = :tenantId and o.seriesId = s.id
              and o.status = com.universalplatform.scheduling.ScheduleOccurrenceStatus.SCHEDULED
              and o.startsAt < :to and o.endsAt > :from and s.subjectId in :subjectIds
            order by o.startsAt asc, o.id asc
            """)
    List<ScheduleOccurrence> findRangeForSubjects(@Param("tenantId") UUID tenantId, @Param("subjectIds") Collection<UUID> subjectIds,
                                                   @Param("from") Instant from, @Param("to") Instant to);

    /** Capped variant of {@link #findRangeForSubjects}. */
    @Query("""
            select o from ScheduleOccurrence o, ScheduleSeries s
            where o.tenantId = :tenantId and s.tenantId = :tenantId and o.seriesId = s.id
              and o.status = com.universalplatform.scheduling.ScheduleOccurrenceStatus.SCHEDULED
              and o.startsAt < :to and o.endsAt > :from and s.subjectId in :subjectIds
            order by o.startsAt asc, o.id asc
            """)
    List<ScheduleOccurrence> findRangeForSubjectsCapped(@Param("tenantId") UUID tenantId,
            @Param("subjectIds") Collection<UUID> subjectIds, @Param("from") Instant from, @Param("to") Instant to,
            Pageable pageable);

    @Query("""
            select o from ScheduleOccurrence o, ScheduleSeries s
            where o.tenantId = :tenantId and s.tenantId = :tenantId and o.seriesId = s.id
              and o.status = com.universalplatform.scheduling.ScheduleOccurrenceStatus.SCHEDULED
              and o.startsAt < :to and o.endsAt > :from and s.moduleId in :moduleIds
            order by o.startsAt asc, o.id asc
            """)
    List<ScheduleOccurrence> findRangeForModules(@Param("tenantId") UUID tenantId, @Param("moduleIds") Collection<UUID> moduleIds,
                                                  @Param("from") Instant from, @Param("to") Instant to);

    /** Capped variant of {@link #findRangeForModules}. */
    @Query("""
            select o from ScheduleOccurrence o, ScheduleSeries s
            where o.tenantId = :tenantId and s.tenantId = :tenantId and o.seriesId = s.id
              and o.status = com.universalplatform.scheduling.ScheduleOccurrenceStatus.SCHEDULED
              and o.startsAt < :to and o.endsAt > :from and s.moduleId in :moduleIds
            order by o.startsAt asc, o.id asc
            """)
    List<ScheduleOccurrence> findRangeForModulesCapped(@Param("tenantId") UUID tenantId,
            @Param("moduleIds") Collection<UUID> moduleIds, @Param("from") Instant from, @Param("to") Instant to,
            Pageable pageable);

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

    /** Capped variant of {@link #findPublicRange}. */
    @Query("""
            select o from ScheduleOccurrence o, ScheduleSeries s
            where o.tenantId = :tenantId and s.tenantId = :tenantId and o.seriesId = s.id
              and o.status = com.universalplatform.scheduling.ScheduleOccurrenceStatus.SCHEDULED
              and o.startsAt < :to and o.endsAt > :from
              and s.kind in (com.universalplatform.scheduling.ScheduleKind.HOLIDAY, com.universalplatform.scheduling.ScheduleKind.EVENT)
              and (s.branchId is null or s.branchId = :branchId)
            order by o.startsAt asc, o.id asc
            """)
    List<ScheduleOccurrence> findPublicRangeCapped(@Param("tenantId") UUID tenantId, @Param("branchId") UUID branchId,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("""
            select o from ScheduleOccurrence o, ScheduleSeries s
            where o.tenantId = :tenantId and s.tenantId = :tenantId and o.seriesId = s.id
              and o.status = com.universalplatform.scheduling.ScheduleOccurrenceStatus.SCHEDULED
              and o.startsAt < :to and o.endsAt > :from and s.branchId in :branchIds
            order by o.startsAt asc, o.id asc
            """)
    List<ScheduleOccurrence> findRangeForBranches(@Param("tenantId") UUID tenantId, @Param("branchIds") Collection<UUID> branchIds,
                                                   @Param("from") Instant from, @Param("to") Instant to);

    /** Capped variant of {@link #findRangeForBranches}. */
    @Query("""
            select o from ScheduleOccurrence o, ScheduleSeries s
            where o.tenantId = :tenantId and s.tenantId = :tenantId and o.seriesId = s.id
              and o.status = com.universalplatform.scheduling.ScheduleOccurrenceStatus.SCHEDULED
              and o.startsAt < :to and o.endsAt > :from and s.branchId in :branchIds
            order by o.startsAt asc, o.id asc
            """)
    List<ScheduleOccurrence> findRangeForBranchesCapped(@Param("tenantId") UUID tenantId,
            @Param("branchIds") Collection<UUID> branchIds, @Param("from") Instant from, @Param("to") Instant to,
            Pageable pageable);

    long countByTenantIdAndSeriesIdAndStatus(UUID tenantId, UUID seriesId, ScheduleOccurrenceStatus status);

    void deleteAllByTenantIdAndSeriesId(UUID tenantId, UUID seriesId);
}
