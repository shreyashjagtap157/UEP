package com.universalplatform.identity;

import com.universalplatform.security.TenantContext;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit read-only identity boundary for other domain modules. */
@Service
public class MembershipDirectory {
    private final TenantContext tenantContext;
    private final IdentitySecurityService identitySecurity;
    private final TenantMembershipRepository memberships;
    private final UserAccountRepository users;
    private final RoleAssignmentRepository roleAssignments;
    private final RoleDefinitionRepository roles;

    MembershipDirectory(TenantContext tenantContext, IdentitySecurityService identitySecurity,
                        TenantMembershipRepository memberships, UserAccountRepository users,
                        RoleAssignmentRepository roleAssignments, RoleDefinitionRepository roles) {
        this.tenantContext = tenantContext;
        this.identitySecurity = identitySecurity;
        this.memberships = memberships;
        this.users = users;
        this.roleAssignments = roleAssignments;
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public MembershipReference current() {
        return reference(identitySecurity.requireCurrentIdentity());
    }

    @Transactional(readOnly = true)
    public MembershipReference requireActive(UUID membershipId) {
        UUID tenantId = tenantContext.requireTenantId();
        TenantMembership membership = memberships.findByTenantIdAndId(tenantId, membershipId)
                .orElseThrow(() -> new IdentityNotFoundException("Membership not found"));
        if (membership.status() != MembershipStatus.ACTIVE) {
            throw new IdentityAccessDeniedException("Membership is not active");
        }
        UserAccount user = users.findById(membership.userId())
                .orElseThrow(() -> new IdentityNotFoundException("User account not found"));
        if (user.status() != UserStatus.ACTIVE) {
            throw new IdentityAccessDeniedException("User account is disabled");
        }
        return reference(membership, user);
    }

    @Transactional(readOnly = true)
    public Map<UUID, MembershipReference> references(Collection<UUID> membershipIds) {
        if (membershipIds == null || membershipIds.isEmpty()) return Map.of();
        UUID tenantId = tenantContext.requireTenantId();
        var found = memberships.findAllByTenantIdAndIdIn(tenantId, membershipIds);
        var userIds = found.stream().map(TenantMembership::userId).collect(Collectors.toSet());
        Map<UUID, UserAccount> userById = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserAccount::id, Function.identity()));
        Map<UUID, MembershipReference> result = new LinkedHashMap<>();
        for (TenantMembership membership : found) {
            UserAccount user = userById.get(membership.userId());
            if (user != null) result.put(membership.id(), reference(membership, user));
        }
        return Map.copyOf(result);
    }


    @Transactional(readOnly = true)
    public java.util.Set<UUID> activeMembershipIds() {
        return java.util.Set.copyOf(memberships.findActiveIds(tenantContext.requireTenantId()));
    }

    @Transactional(readOnly = true)
    public java.util.Set<UUID> activeMembershipIdsForBranch(UUID branchId) {
        return java.util.Set.copyOf(memberships.findActiveIdsByBranch(tenantContext.requireTenantId(), branchId));
    }

    @Transactional(readOnly = true)
    public java.util.Set<UUID> activeMembershipIdsForRole(UUID roleId) {
        return java.util.Set.copyOf(roleAssignments.findActiveMembershipIdsByRole(tenantContext.requireTenantId(), roleId));
    }


    @Transactional(readOnly = true)
    public RoleReference requireRole(UUID roleId) {
        UUID tenantId = tenantContext.requireTenantId();
        RoleDefinition role = roles.findByTenantIdAndId(tenantId, roleId)
                .orElseThrow(() -> new IdentityNotFoundException("Role not found"));
        return new RoleReference(role.id(), role.name(), role.systemManaged());
    }

    private static MembershipReference reference(ActiveIdentity identity) {
        return reference(identity.membership(), identity.user());
    }

    private static MembershipReference reference(TenantMembership membership, UserAccount user) {
        return new MembershipReference(membership.id(), user.id(), user.oidcSubject(), user.email(), user.displayName(),
                membership.primaryBranchId(), membership.status());
    }

    public record RoleReference(UUID id, String name, boolean systemManaged) {}

    public record MembershipReference(
            UUID membershipId, UUID userId, String oidcSubject, String email, String displayName,
            UUID primaryBranchId, MembershipStatus status) {}
}
