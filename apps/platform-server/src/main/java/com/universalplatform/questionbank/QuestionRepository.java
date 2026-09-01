package com.universalplatform.questionbank;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface QuestionRepository extends JpaRepository<Question, UUID> {
    Optional<Question> findByTenantIdAndId(UUID tenantId, UUID id);
    Page<Question> findAllByTenantId(UUID tenantId, Pageable pageable);
}
