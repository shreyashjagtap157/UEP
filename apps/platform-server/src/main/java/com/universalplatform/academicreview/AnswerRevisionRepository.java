package com.universalplatform.academicreview;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface AnswerRevisionRepository extends JpaRepository<AnswerRevision,UUID>{Optional<AnswerRevision> findByTenantIdAndId(UUID tenantId,UUID id); List<AnswerRevision> findAllByTenantIdAndReviewCaseIdOrderByRevisionNumberDesc(UUID tenantId,UUID reviewCaseId); Optional<AnswerRevision> findTopByTenantIdAndReviewCaseIdAndAnswerIdOrderByRevisionNumberDesc(UUID tenantId,UUID reviewCaseId,UUID answerId);}
