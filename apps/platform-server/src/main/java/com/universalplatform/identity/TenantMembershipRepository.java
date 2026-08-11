package com.universalplatform.identity;

import java.util.Collection;
import java.util.List;
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
    List<TenantMembership> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);
    long countByTenantId(UUID tenantId);

    @Query("""
            select m.id from TenantMembership m
            where m.tenantId = :tenantId and m.status = com.universalplatform.identity.MembershipStatus.ACTIVE
            """)
    List<UUID> findActiveIds(@Param("tenantId") UUID tenantId);

    @Query("""
            select m.id from TenantMembership m
            where m.tenantId = :tenantId and m.primaryBranchId = :branchId
              and m.status = com.universalplatform.identity.MembershipStatus.ACTIVE
            """)
    List<UUID> findActiveIdsByBranch(@Param("tenantId") UUID tenantId, @Param("branchId") UUID branchId);

    @Query("""
            select m from TenantMembership m, UserAccount u
            where m.tenantId = :tenantId and m.userId = u.id and u.oidcSubject = :subject
            """)
    Optional<TenantMembership> findByTenantAndSubject(@Param("tenantId") UUID tenantId, @Param("subject") String subject);
}
