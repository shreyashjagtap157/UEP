package com.universalplatform.identity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
    void deleteAllByIdRoleId(UUID roleId);
    List<RolePermission> findAllByIdRoleId(UUID roleId);
    List<RolePermission> findAllByIdRoleIdIn(Collection<UUID> roleIds);

    @Query("""
            select distinct rp.id.permissionKey
            from RolePermission rp, RoleAssignment ra
            where ra.tenantId = :tenantId
              and ra.membershipId = :membershipId
              and ra.roleId = rp.id.roleId
              and (ra.scopeKind = com.universalplatform.identity.AssignmentScopeKind.TENANT
                   or (ra.scopeKind = com.universalplatform.identity.AssignmentScopeKind.BRANCH and ra.scopeId = :branchId))
            """)
    List<String> findPermissionKeysForMembership(
            @Param("tenantId") UUID tenantId,
            @Param("membershipId") UUID membershipId,
            @Param("branchId") UUID branchId);
}
