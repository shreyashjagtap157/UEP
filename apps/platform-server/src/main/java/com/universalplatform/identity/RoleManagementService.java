package com.universalplatform.identity;

import com.universalplatform.audit.AuditService;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RoleManagementService {
    private final TenantContext tenantContext;
    private final ActorContext actorContext;
    private final AuthorizationService authorization;
    private final RoleDefinitionRepository roles;
    private final RolePermissionRepository permissions;
    private final RoleAssignmentRepository assignments;
    private final TenantMembershipRepository memberships;
    private final DefaultRoleSeeder roleSeeder;
    private final OwnershipGuard ownershipGuard;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    RoleManagementService(TenantContext tenantContext, ActorContext actorContext, AuthorizationService authorization,
                          RoleDefinitionRepository roles, RolePermissionRepository permissions,
                          RoleAssignmentRepository assignments, TenantMembershipRepository memberships,
                          DefaultRoleSeeder roleSeeder, OwnershipGuard ownershipGuard, AuditService audit) {
        this.tenantContext = tenantContext;
        this.actorContext = actorContext;
        this.authorization = authorization;
        this.roles = roles;
        this.permissions = permissions;
        this.assignments = assignments;
        this.memberships = memberships;
        this.roleSeeder = roleSeeder;
        this.ownershipGuard = ownershipGuard;
        this.audit = audit;
    }

    @Transactional
    IdentityPage<RoleView> list(int page, int size) {
        authorization.require(PermissionKey.ROLES_VIEW);
        UUID tenantId = tenantContext.requireTenantId();
        roleSeeder.ensureSystemRoles(tenantId);
        int safePage = Math.max(0, page);
        int safeSize = size < 1 ? 25 : Math.min(size, 100);
        Page<RoleDefinition> result = roles.findAllByTenantId(
                tenantId, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "systemManaged").and(Sort.by("name"))));
        Map<UUID, Set<PermissionKey>> permissionMap = new LinkedHashMap<>();
        List<UUID> roleIds = result.getContent().stream().map(RoleDefinition::id).toList();
        for (RolePermission permission : permissions.findAllByIdRoleIdIn(roleIds)) {
            permissionMap.computeIfAbsent(permission.id().roleId(), ignored -> EnumSet.noneOf(PermissionKey.class))
                    .add(PermissionKey.valueOf(permission.id().permissionKey()));
        }
        List<RoleView> views = result.getContent().stream()
                .map(role -> toView(role, permissionMap.getOrDefault(role.id(), Set.of())))
                .toList();
        return new IdentityPage<>(views, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    RoleView createCustom(String name, String description, Collection<PermissionKey> requestedPermissions) {
        authorization.require(PermissionKey.ROLES_MANAGE);
        UUID tenantId = tenantContext.requireTenantId();
        String normalizedName = required(name, "Role name", 120);
        if (roles.existsByTenantIdAndNameIgnoreCase(tenantId, normalizedName)) {
            throw new IdentityConflictException("A role with this name already exists");
        }
        RoleDefinition role = roles.save(new RoleDefinition(UUID.randomUUID(), tenantId, normalizedName,
                normalizeNullable(description, 500), clock.instant()));
        replacePermissions(role.id(), requestedPermissions);
        audit.record(tenantId, actorContext.requireSubject(), "CUSTOM_ROLE_CREATED", "role_definition", role.id().toString());
        return toView(role);
    }

    @Transactional
    RoleView updateCustom(UUID roleId, String name, String description,
                          Collection<PermissionKey> requestedPermissions, long expectedVersion) {
        authorization.require(PermissionKey.ROLES_MANAGE);
        UUID tenantId = tenantContext.requireTenantId();
        RoleDefinition role = role(tenantId, roleId);
        if (role.systemManaged()) throw new IdentityConflictException("System-managed roles cannot be modified");
        if (role.version() != expectedVersion) throw new IdentityConflictException("Role was modified by another request");
        String normalizedName = required(name, "Role name", 120);
        if (!role.name().equalsIgnoreCase(normalizedName) && roles.existsByTenantIdAndNameIgnoreCase(tenantId, normalizedName)) {
            throw new IdentityConflictException("A role with this name already exists");
        }
        role.updateCustom(normalizedName, normalizeNullable(description, 500), clock.instant());
        replacePermissions(role.id(), requestedPermissions);
        audit.record(tenantId, actorContext.requireSubject(), "CUSTOM_ROLE_UPDATED", "role_definition", role.id().toString());
        return toView(role);
    }

    @Transactional
    void deleteCustom(UUID roleId) {
        authorization.require(PermissionKey.ROLES_MANAGE);
        UUID tenantId = tenantContext.requireTenantId();
        RoleDefinition role = role(tenantId, roleId);
        if (role.systemManaged()) throw new IdentityConflictException("System-managed roles cannot be deleted");
        if (assignments.countByRoleId(role.id()) != 0) throw new IdentityConflictException("Role is still assigned to users");
        roles.delete(role);
        audit.record(tenantId, actorContext.requireSubject(), "CUSTOM_ROLE_DELETED", "role_definition", role.id().toString());
    }

    @Transactional
    AssignmentView assign(UUID membershipId, UUID roleId, AssignmentScopeKind scopeKind, UUID scopeId) {
        authorization.require(PermissionKey.ROLES_ASSIGN);
        UUID tenantId = tenantContext.requireTenantId();
        TenantMembership membership = memberships.findByTenantIdAndId(tenantId, membershipId)
                .orElseThrow(() -> new IdentityNotFoundException("Membership not found"));
        if (membership.status() == MembershipStatus.ENDED) throw new IdentityConflictException("Cannot assign roles to an ended membership");
        RoleDefinition role = role(tenantId, roleId);
        validateScope(scopeKind, scopeId);
        if (role.systemManaged() && SystemRoleKey.ORGANIZATION_OWNER.name().equals(role.systemKey())
                && scopeKind != AssignmentScopeKind.TENANT) {
            throw new IdentityConflictException("Organization Owner must be assigned at tenant scope");
        }
        RoleAssignment assignment = assignments.save(new RoleAssignment(UUID.randomUUID(), tenantId, membership.id(), role.id(),
                scopeKind, scopeId, actorContext.requireSubject(), clock.instant()));
        audit.record(tenantId, actorContext.requireSubject(), "ROLE_ASSIGNED", "role_assignment", assignment.id().toString());
        return new AssignmentView(assignment.id(), membership.id(), role.id(), role.name(), scopeKind, scopeId);
    }

    @Transactional
    void unassign(UUID assignmentId) {
        authorization.require(PermissionKey.ROLES_ASSIGN);
        UUID tenantId = tenantContext.requireTenantId();
        RoleAssignment assignment = assignments.findByTenantIdAndId(tenantId, assignmentId)
                .orElseThrow(() -> new IdentityNotFoundException("Role assignment not found"));
        RoleDefinition role = role(tenantId, assignment.roleId());
        ownershipGuard.assertCanRemoveAssignment(assignment, role);
        assignments.delete(assignment);
        audit.record(tenantId, actorContext.requireSubject(), "ROLE_UNASSIGNED", "role_assignment", assignment.id().toString());
    }

    @Transactional(readOnly = true)
    List<AssignmentView> assignmentsForMember(UUID membershipId) {
        authorization.require(PermissionKey.ROLES_VIEW);
        UUID tenantId = tenantContext.requireTenantId();
        memberships.findByTenantIdAndId(tenantId, membershipId)
                .orElseThrow(() -> new IdentityNotFoundException("Membership not found"));
        List<RoleAssignment> rows = assignments.findAllByTenantIdAndMembershipId(tenantId, membershipId);
        return rows.stream().map(row -> {
            RoleDefinition role = role(tenantId, row.roleId());
            return new AssignmentView(row.id(), row.membershipId(), role.id(), role.name(), row.scopeKind(), row.scopeId());
        }).sorted(Comparator.comparing(AssignmentView::roleName)).toList();
    }

    private RoleView toView(RoleDefinition role) {
        Set<PermissionKey> rolePermissions = permissions.findAllByIdRoleId(role.id()).stream()
                .map(item -> PermissionKey.valueOf(item.id().permissionKey()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return toView(role, rolePermissions);
    }

    private static RoleView toView(RoleDefinition role, Set<PermissionKey> rolePermissions) {
        return new RoleView(role.id(), role.systemKey(), role.name(), role.description(), role.systemManaged(),
                Set.copyOf(rolePermissions), role.version());
    }

    private void replacePermissions(UUID roleId, Collection<PermissionKey> requestedPermissions) {
        Set<PermissionKey> normalized = requestedPermissions == null || requestedPermissions.isEmpty()
                ? Set.of() : Set.copyOf(requestedPermissions);
        permissions.deleteAllByIdRoleId(roleId);
        permissions.saveAll(normalized.stream()
                .map(permission -> new RolePermission(new RolePermissionId(roleId, permission)))
                .toList());
    }

    private RoleDefinition role(UUID tenantId, UUID roleId) {
        return roles.findByTenantIdAndId(tenantId, roleId)
                .orElseThrow(() -> new IdentityNotFoundException("Role not found"));
    }

    private static void validateScope(AssignmentScopeKind scopeKind, UUID scopeId) {
        if (scopeKind == null) throw new IllegalArgumentException("Role scope is required");
        if (scopeKind == AssignmentScopeKind.TENANT && scopeId != null) {
            throw new IllegalArgumentException("Tenant-scoped role assignment cannot include a scope id");
        }
        if (scopeKind == AssignmentScopeKind.BRANCH && scopeId == null) {
            throw new IllegalArgumentException("Branch-scoped role assignment requires a branch id");
        }
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        return normalized;
    }

    private static String normalizeNullable(String value, int maxLength) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > maxLength) throw new IllegalArgumentException("Value exceeds " + maxLength + " characters");
        return normalized;
    }

    record RoleView(UUID id, String systemKey, String name, String description, boolean systemManaged,
                    Set<PermissionKey> permissions, long version) {}
    record AssignmentView(UUID id, UUID membershipId, UUID roleId, String roleName,
                          AssignmentScopeKind scopeKind, UUID scopeId) {}
}
