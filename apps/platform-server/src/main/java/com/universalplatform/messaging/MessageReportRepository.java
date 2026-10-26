package com.universalplatform.messaging;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface MessageReportRepository extends JpaRepository<MessageReport, UUID> {
    boolean existsByTenantIdAndMessageIdAndReporterMembershipId(
            UUID tenantId, UUID messageId, UUID reporterMembershipId);

    Optional<MessageReport> findByTenantIdAndId(UUID tenantId, UUID id);

    Page<MessageReport> findAllByTenantId(UUID tenantId, Pageable pageable);
}
