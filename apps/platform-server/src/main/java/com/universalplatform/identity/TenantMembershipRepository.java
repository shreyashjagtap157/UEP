package com.universalplatform.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TenantMembershipRepository extends JpaRepository<TenantMembership, UUID> {
    Optional<TenantMembership> findByTenantIdAndUserId(UUID tenantId, UUID userId);
    Optional<TenantMembership> findByTenantIdAndId(UUID tenantId, UUID id);
    Page<TenantMembership> findAllByTenantId(UUID tenantId, Pageable pageable);
    long countByTenantId(UUID tenantId);

    @Query("""
            select m from TenantMembership m, UserAccount u
            where m.tenantId = :tenantId and m.userId = u.id and u.oidcSubject = :subject
            """)
    Optional<TenantMembership> findByTenantAndSubject(@Param("tenantId") UUID tenantId, @Param("subject") String subject);
}
