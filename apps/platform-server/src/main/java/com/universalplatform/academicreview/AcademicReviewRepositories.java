package com.universalplatform.academicreview;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface ReviewCaseRepository extends JpaRepository<ReviewCase,UUID>{Optional<ReviewCase> findByTenantIdAndId(UUID tenantId,UUID id); List<ReviewCase> findAllByTenantIdAndAttemptIdOrderByCreatedAtDesc(UUID tenantId,UUID attemptId);}
interface ReviewCommentRepository extends JpaRepository<ReviewComment,UUID>{List<ReviewComment> findAllByTenantIdAndReviewCaseIdOrderByCreatedAtAsc(UUID tenantId,UUID reviewCaseId);}
interface ReviewImpactRepository extends JpaRepository<ReviewImpact,UUID>{List<ReviewImpact> findAllByTenantIdAndReviewCaseIdOrderByCreatedAtDesc(UUID tenantId,UUID reviewCaseId);}
