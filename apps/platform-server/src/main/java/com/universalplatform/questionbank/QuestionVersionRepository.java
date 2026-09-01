package com.universalplatform.questionbank;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface QuestionVersionRepository extends JpaRepository<QuestionVersion, UUID> {
    Optional<QuestionVersion> findByTenantIdAndId(UUID tenantId, UUID id);
    List<QuestionVersion> findAllByTenantIdAndQuestionIdOrderByVersionNumberDesc(UUID tenantId, UUID questionId);
    Optional<QuestionVersion> findTopByTenantIdAndQuestionIdOrderByVersionNumberDesc(UUID tenantId, UUID questionId);
}
