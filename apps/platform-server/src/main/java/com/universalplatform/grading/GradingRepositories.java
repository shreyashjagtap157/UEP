package com.universalplatform.grading;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

interface GradeRevisionRepository extends JpaRepository<GradeRevision,UUID> {
    Optional<GradeRevision> findByTenantIdAndId(UUID tenantId, UUID id);
    List<GradeRevision> findAllByTenantIdAndAttemptIdOrderByRevisionNumberDesc(UUID tenantId, UUID attemptId);
    Optional<GradeRevision> findTopByTenantIdAndAttemptIdOrderByRevisionNumberDesc(UUID tenantId, UUID attemptId);
}
interface GradeItemRepository extends JpaRepository<GradeItem,UUID> {
    List<GradeItem> findAllByTenantIdAndGradeRevisionId(UUID tenantId, UUID gradeRevisionId);
    Optional<GradeItem> findByTenantIdAndGradeRevisionIdAndAnswerId(UUID tenantId, UUID gradeRevisionId, UUID answerId);
}
