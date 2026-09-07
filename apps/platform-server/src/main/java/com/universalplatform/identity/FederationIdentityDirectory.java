package com.universalplatform.identity;

import com.universalplatform.audit.AuditService;
import com.universalplatform.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public identity boundary for trusted enterprise-federation claim synchronization. */
@Service
public class FederationIdentityDirectory {
    private final TenantContext tenantContext;
    private final TenantMembershipRepository memberships;
    private final UserAccountRepository users;
    private final RoleDefinitionRepository roles;
    private final RoleAssignmentRepository assignments;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    FederationIdentityDirectory(TenantContext tenantContext, TenantMembershipRepository memberships,
                                UserAccountRepository users, RoleDefinitionRepository roles,
                                RoleAssignmentRepository assignments, AuditService audit) {
        this.tenantContext = tenantContext; this.memberships = memberships; this.users = users;
        this.roles = roles; this.assignments = assignments; this.audit = audit;
    }

    @Transactional
    public SyncResult synchronize(String providerKey, String subject, String email, String displayName,
                                  UUID primaryBranchId, String externalReference, Set<String> groups,
                                  Map<String, SystemRoleKey> roleMappings, boolean active) {
        UUID tenantId = tenantContext.requireTenantId();
        String normalizedSubject = required(subject, "subject", 160);
        Instant now = clock.instant();
        UserAccount user = users.findByOidcSubject(normalizedSubject)
                .orElseGet(() -> users.save(new UserAccount(UUID.randomUUID(), normalizedSubject,
                        nullable(email, 320), required(displayName, "displayName", 200), now)));
        if (!active) {
            TenantMembership membership = memberships.findByTenantIdAndUserId(tenantId, user.id()).orElse(null);
            if (membership == null) return new SyncResult(null, user.id(), false, Set.of());
            membership.changeStatus(MembershipStatus.ENDED, now);
            assignments.deleteAllByTenantIdAndMembershipIdAndSource(tenantId, membership.id(), RoleAssignmentSource.FEDERATED);
            audit.record(tenantId, "federation:" + providerKey, "FEDERATION_MEMBERSHIP_DEPROVISIONED", "tenant_membership", membership.id().toString());
            return new SyncResult(membership.id(), user.id(), false, Set.of());
        }
        user.refreshProfile(nullable(email, 320), required(displayName, "displayName", 200), now);
        TenantMembership membership = memberships.findByTenantIdAndUserId(tenantId, user.id()).orElse(null);
        if (membership == null) {
            membership = memberships.save(new TenantMembership(UUID.randomUUID(), tenantId, user.id(), primaryBranchId,
                    nullable(externalReference, 160), now));
        } else {
            if (membership.status() != MembershipStatus.ACTIVE) membership.changeStatus(MembershipStatus.ACTIVE, now);
            membership.update(primaryBranchId, nullable(externalReference, 160));
        }
        assignments.deleteAllByTenantIdAndMembershipIdAndSource(tenantId, membership.id(), RoleAssignmentSource.FEDERATED);
        Set<SystemRoleKey> mapped = new LinkedHashSet<>();
        for (String group : groups == null ? Set.<String>of() : groups) {
            SystemRoleKey roleKey = roleMappings == null ? null : roleMappings.get(group);
            if (roleKey == null) continue;
            RoleDefinition role = roles.findByTenantIdAndSystemKey(tenantId, roleKey.name())
                    .orElseThrow(() -> new IdentityNotFoundException("System role not found: " + roleKey));
            assignments.save(new RoleAssignment(UUID.randomUUID(), tenantId, membership.id(), role.id(),
                    AssignmentScopeKind.TENANT, null, "federation:" + providerKey, now, RoleAssignmentSource.FEDERATED));
            mapped.add(roleKey);
        }
        audit.record(tenantId, "federation:" + providerKey, "FEDERATION_MEMBERSHIP_SYNCHRONIZED", "tenant_membership", membership.id().toString());
        return new SyncResult(membership.id(), user.id(), true, Set.copyOf(mapped));
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) throw new IllegalArgumentException("Invalid " + field);
        return value.trim();
    }
    private static String nullable(String value, int max) { if (value == null || value.isBlank()) return null; return required(value, "value", max); }
    public record SyncResult(UUID membershipId, UUID userId, boolean active, Set<SystemRoleKey> mappedRoles) {}
}
