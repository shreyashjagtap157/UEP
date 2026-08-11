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

interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, UUID> {
    List<RoleAssignment> findAllByTenantIdAndMembershipId(UUID tenantId, UUID membershipId);
    Page<RoleAssignment> findPageByTenantIdAndMembershipId(UUID tenantId, UUID membershipId, Pageable pageable);
    List<RoleAssignment> findAllByTenantIdAndMembershipIdIn(UUID tenantId, Collection<UUID> membershipIds);
    long countByRoleId(UUID roleId);
    boolean existsByTenantIdAndMembershipIdAndRoleId(UUID tenantId, UUID membershipId, UUID roleId);

    @Query("""
            select count(ra) from RoleAssignment ra, TenantMembership m
            where ra.tenantId = :tenantId and ra.roleId = :roleId
              and ra.membershipId = m.id and m.tenantId = :tenantId
              and m.status = com.universalplatform.identity.MembershipStatus.ACTIVE
              and ra.scopeKind = com.universalplatform.identity.AssignmentScopeKind.TENANT
            """)
    long countActiveTenantAssignments(@Param("tenantId") UUID tenantId, @Param("roleId") UUID roleId);
    Optional<RoleAssignment> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("""
            select distinct ra.membershipId from RoleAssignment ra, TenantMembership m
            where ra.tenantId = :tenantId and ra.roleId = :roleId
              and m.id = ra.membershipId and m.tenantId = :tenantId
              and m.status = com.universalplatform.identity.MembershipStatus.ACTIVE
            """)
    List<UUID> findActiveMembershipIdsByRole(@Param("tenantId") UUID tenantId, @Param("roleId") UUID roleId);
}
