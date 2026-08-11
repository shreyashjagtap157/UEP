package com.universalplatform.identity;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class OwnershipGuard {
    private final RoleDefinitionRepository roles;
    private final RoleAssignmentRepository assignments;

    OwnershipGuard(RoleDefinitionRepository roles, RoleAssignmentRepository assignments) {
        this.roles = roles;
        this.assignments = assignments;
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

    private void assertAnotherActiveOwnerExists(UUID tenantId, RoleDefinition ownerRole) {
        if (assignments.countActiveTenantAssignments(tenantId, ownerRole.id()) <= 1) {
            throw new IdentityConflictException("At least one active Organization Owner must remain");
        }
    }
}
