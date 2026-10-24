package com.universalplatform.identity;

import com.universalplatform.security.TenantMutationLock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class OwnershipGuard {
    private final RoleDefinitionRepository roles;
    private final RoleAssignmentRepository assignments;
    private final TenantMembershipRepository memberships;
    private final TenantMutationLock tenantMutationLock;

    OwnershipGuard(RoleDefinitionRepository roles, RoleAssignmentRepository assignments,
                   TenantMembershipRepository memberships, TenantMutationLock tenantMutationLock) {
        this.roles = roles;
        this.assignments = assignments;
        this.memberships = memberships;
        this.tenantMutationLock = tenantMutationLock;
    }

    void assertCanDeactivateMembership(UUID tenantId, UUID membershipId) {
        tenantMutationLock.lockTenant(tenantId);
        roles.findByTenantIdAndSystemKey(tenantId, SystemRoleKey.ORGANIZATION_OWNER.name())
                .filter(role -> assignments.existsByTenantIdAndMembershipIdAndRoleId(tenantId, membershipId, role.id()))
                .ifPresent(role -> assertAnotherActiveOwnerExists(tenantId, role));
    }

    void assertCanRemoveAssignment(RoleAssignment assignment, RoleDefinition role) {
        if (role.systemManaged() && SystemRoleKey.ORGANIZATION_OWNER.name().equals(role.systemKey())) {
            tenantMutationLock.lockTenant(assignment.tenantId());
            assertAnotherActiveOwnerExists(assignment.tenantId(), role);
        }
    }

    boolean isPrivilegedRole(RoleDefinition role) {
        return role.systemManaged() && (SystemRoleKey.ORGANIZATION_OWNER.name().equals(role.systemKey())
                || SystemRoleKey.ORGANIZATION_ADMINISTRATOR.name().equals(role.systemKey()));
    }

    void assertCanManageCredentialsForAnotherMembership(UUID tenantId, UUID actorMembershipId,
                                                               boolean platformSuperAdmin) {
        tenantMutationLock.lockTenant(tenantId);
        if (platformSuperAdmin) return;
        RoleDefinition ownerRole = roles.findByTenantIdAndSystemKey(tenantId, SystemRoleKey.ORGANIZATION_OWNER.name())
                .orElseThrow(() -> new IdentityConflictException("Organization Owner role is not configured"));
        TenantMembership actorMembership = memberships.findByTenantIdAndId(tenantId, actorMembershipId)
                .filter(membership -> membership.status() == MembershipStatus.ACTIVE)
                .orElseThrow(() -> new IdentityAccessDeniedException(
                        "An active Organization Owner is required to create an API credential for another membership"));
        boolean isOwner = assignments.existsByTenantIdAndMembershipIdAndRoleIdAndScopeKind(
                tenantId, actorMembership.id(), ownerRole.id(), AssignmentScopeKind.TENANT);
        if (!isOwner) {
            throw new IdentityAccessDeniedException(
                    "Only an active Organization Owner or platform super administrator can create an API credential for another membership");
        }
    }

    void assertCanAssignPrivilegedRole(UUID tenantId, RoleDefinition requestedRole, String actorSubject,
                                       boolean platformSuperAdmin) {
        tenantMutationLock.lockTenant(tenantId);
        if (platformSuperAdmin) return;
        RoleDefinition ownerRole = roles.findByTenantIdAndSystemKey(tenantId, SystemRoleKey.ORGANIZATION_OWNER.name())
                .orElseThrow(() -> new IdentityConflictException("Organization Owner role is not configured"));
        TenantMembership actorMembership = memberships.findByTenantAndSubject(tenantId, actorSubject)
                .filter(membership -> membership.status() == MembershipStatus.ACTIVE)
                .orElseThrow(() -> new IdentityAccessDeniedException(
                        "An active tenant Organization Owner is required to assign privileged administrative roles"));
        boolean isOwner = assignments.existsByTenantIdAndMembershipIdAndRoleIdAndScopeKind(
                tenantId, actorMembership.id(), ownerRole.id(), AssignmentScopeKind.TENANT);
        if (!isOwner) {
            throw new IdentityAccessDeniedException(
                    "Only an existing Organization Owner or platform super administrator can assign "
                            + requestedRole.name());
        }
    }

    private void assertAnotherActiveOwnerExists(UUID tenantId, RoleDefinition ownerRole) {
        if (assignments.countActiveTenantAssignments(tenantId, ownerRole.id()) <= 1) {
            throw new IdentityConflictException("At least one active Organization Owner must remain");
        }
    }
}
