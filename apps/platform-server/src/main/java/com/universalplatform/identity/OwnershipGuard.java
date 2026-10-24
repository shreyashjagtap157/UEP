package com.universalplatform.identity;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class OwnershipGuard {
    private final RoleDefinitionRepository roles;
    private final RoleAssignmentRepository assignments;
    private final TenantMembershipRepository memberships;

    OwnershipGuard(RoleDefinitionRepository roles, RoleAssignmentRepository assignments,
                   TenantMembershipRepository memberships) {
        this.roles = roles;
        this.assignments = assignments;
        this.memberships = memberships;
    }

    void assertCanDeactivateMembership(UUID tenantId, UUID membershipId) {
        roles.findByTenantIdAndSystemKey(tenantId, SystemRoleKey.ORGANIZATION_OWNER.name())
                .filter(role -> assignments.existsByTenantIdAndMembershipIdAndRoleId(tenantId, membershipId, role.id()))
                .ifPresent(role -> assertAnotherActiveOwnerExists(tenantId, role));
    }

    void assertCanRemoveAssignment(RoleAssignment assignment, RoleDefinition role) {
        if (role.systemManaged() && SystemRoleKey.ORGANIZATION_OWNER.name().equals(role.systemKey())) {
            assertAnotherActiveOwnerExists(assignment.tenantId(), role);
        }
    }

    boolean isPrivilegedRole(RoleDefinition role) {
        return role.systemManaged() && (SystemRoleKey.ORGANIZATION_OWNER.name().equals(role.systemKey())
                || SystemRoleKey.ORGANIZATION_ADMINISTRATOR.name().equals(role.systemKey()));
    }

    void assertCanAssignPrivilegedRole(UUID tenantId, RoleDefinition requestedRole, String actorSubject,
                                       boolean platformSuperAdmin) {
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
