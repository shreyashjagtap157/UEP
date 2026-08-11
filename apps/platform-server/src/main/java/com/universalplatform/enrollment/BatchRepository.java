package com.universalplatform.enrollment;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BatchRepository extends JpaRepository<Batch, UUID> {
    Optional<Batch> findByTenantIdAndId(UUID tenantId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Batch b where b.tenantId = :tenantId and b.id = :id")
    Optional<Batch> findByTenantIdAndIdForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

    @Query("""
            select b from Batch b
            where b.tenantId = :tenantId
              and (:academicPeriodId is null or b.academicPeriodId = :academicPeriodId)
              and (:programId is null or b.programId = :programId)
              and (:courseId is null or b.courseId = :courseId)
              and (:branchId is null or b.branchId = :branchId)
            """)
    Page<Batch> search(@Param("tenantId") UUID tenantId,
                       @Param("academicPeriodId") UUID academicPeriodId,
                       @Param("programId") UUID programId,
                       @Param("courseId") UUID courseId,
                       @Param("branchId") UUID branchId,
                       Pageable pageable);
}
