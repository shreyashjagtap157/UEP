package com.universalplatform.identity;

import com.universalplatform.audit.AuditService;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IdentityAdministrationService {
    private final TenantContext tenantContext;
    private final ActorContext actorContext;
    private final AuthorizationService authorization;
    private final IdentitySecurityService identitySecurity;
    private final UserAccountRepository users;
    private final TenantMembershipRepository memberships;
    private final RoleDefinitionRepository roles;
    private final RoleAssignmentRepository assignments;
    private final DefaultRoleSeeder roleSeeder;
    private final OwnershipGuard ownershipGuard;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    IdentityAdministrationService(TenantContext tenantContext, ActorContext actorContext, AuthorizationService authorization,
                                  IdentitySecurityService identitySecurity, UserAccountRepository users, TenantMembershipRepository memberships,
                                  RoleDefinitionRepository roles, RoleAssignmentRepository assignments,
                                  DefaultRoleSeeder roleSeeder, OwnershipGuard ownershipGuard, AuditService audit) {
        this.tenantContext = tenantContext;
        this.actorContext = actorContext;
        this.authorization = authorization;
        this.identitySecurity = identitySecurity;
        this.users = users;
        this.memberships = memberships;
        this.roles = roles;
        this.assignments = assignments;
        this.roleSeeder = roleSeeder;
        this.ownershipGuard = ownershipGuard;
        this.audit = audit;
    }

    @Transactional
    MemberView bootstrapOwner(ProvisionMembershipCommand command) {
        if (!actorContext.hasRealmRole(AuthorizationService.PLATFORM_SUPER_ADMIN_ROLE)) {
            throw new IdentityAccessDeniedException("Platform super administrator role is required for tenant bootstrap");
        }
        UUID tenantId = tenantContext.requireTenantId();
        if (memberships.countByTenantId(tenantId) != 0) {
            throw new IdentityConflictException("Tenant identity bootstrap is already complete");
        }
        Map<SystemRoleKey, RoleDefinition> systemRoles = roleSeeder.ensureSystemRoles(tenantId);
        UserAccount user = upsertUser(command.subject(), command.email(), command.displayName());
        TenantMembership membership = createMembership(tenantId, user, command.primaryBranchId(), command.externalReference());
        RoleDefinition owner = systemRoles.get(SystemRoleKey.ORGANIZATION_OWNER);
        assignments.save(new RoleAssignment(UUID.randomUUID(), tenantId, membership.id(), owner.id(),
                AssignmentScopeKind.TENANT, null, actorContext.requireSubject(), clock.instant()));
        audit.record(tenantId, actorContext.requireSubject(), "TENANT_OWNER_BOOTSTRAPPED", "tenant_membership", membership.id().toString());
        return memberView(membership, user, List.of(owner));
    }

    @Transactional
    MemberView provision(ProvisionMembershipCommand command) {
        authorization.require(PermissionKey.USERS_MANAGE);
        if (command.roleIds() != null && !command.roleIds().isEmpty()) authorization.require(PermissionKey.ROLES_ASSIGN);
        UUID tenantId = tenantContext.requireTenantId();
        roleSeeder.ensureSystemRoles(tenantId);
        UserAccount user = upsertUser(command.subject(), command.email(), command.displayName());
        if (memberships.findByTenantIdAndUserId(tenantId, user.id()).isPresent()) {
            throw new IdentityConflictException("User already has a membership in this tenant");
        }
        TenantMembership membership = createMembership(tenantId, user, command.primaryBranchId(), command.externalReference());
        List<RoleDefinition> assignedRoles = assignInitialRoles(tenantId, membership.id(), command.roleIds());
        audit.record(tenantId, actorContext.requireSubject(), "MEMBERSHIP_CREATED", "tenant_membership", membership.id().toString());
        return memberView(membership, user, assignedRoles);
    }

    @Transactional(readOnly = true)
    IdentityPage<MemberView> list(int page, int size) {
        authorization.require(PermissionKey.USERS_VIEW);
        int safePage = Math.max(0, page);
        int safeSize = boundedSize(size);
        UUID tenantId = tenantContext.requireTenantId();
        Page<TenantMembership> result = memberships.findAllByTenantId(
                tenantId, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "joinedAt", "id")));
        List<MemberView> views = memberViews(tenantId, result.getContent());
        return new IdentityPage<>(views, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    MemberView get(UUID membershipId) {
        authorization.require(PermissionKey.USERS_VIEW);
        UUID tenantId = tenantContext.requireTenantId();
        TenantMembership membership = membership(tenantId, membershipId);
        UserAccount user = users.findById(membership.userId())
                .orElseThrow(() -> new IdentityNotFoundException("User account not found"));
        return memberView(membership, user, rolesForMembership(tenantId, membership.id()));
    }

    @Transactional
    MemberView update(UUID membershipId, UpdateMembershipCommand command) {
        authorization.require(PermissionKey.USERS_MANAGE);
        UUID tenantId = tenantContext.requireTenantId();
        TenantMembership membership = membership(tenantId, membershipId);
        if (membership.version() != command.expectedVersion()) {
            throw new IdentityConflictException("Membership was modified by another request");
        }
        if (membership.status() == MembershipStatus.ACTIVE && command.status() != MembershipStatus.ACTIVE) {
            ownershipGuard.assertCanDeactivateMembership(tenantId, membership.id());
        }
        membership.update(command.primaryBranchId(), normalizeNullable(command.externalReference(), 160));
        if (membership.status() != command.status()) membership.changeStatus(command.status(), clock.instant());
        audit.record(tenantId, actorContext.requireSubject(), "MEMBERSHIP_UPDATED", "tenant_membership", membership.id().toString());
        UserAccount user = users.findById(membership.userId())
                .orElseThrow(() -> new IdentityNotFoundException("User account not found"));
        return memberView(membership, user, rolesForMembership(tenantId, membership.id()));
    }

    @Transactional(readOnly = true)
    CurrentIdentityView currentIdentity() {
        ActiveIdentity identity = identitySecurity.requireCurrentIdentity();
        UUID tenantId = tenantContext.requireTenantId();
        List<RoleDefinition> assignedRoles = rolesForMembership(tenantId, identity.membershipId());
        Set<PermissionKey> permissions = authorization.currentPermissions(null);
        return new CurrentIdentityView(
                identity.userId(), identity.membershipId(), identity.user().oidcSubject(), identity.user().email(),
                identity.user().displayName(), tenantId, identity.membership().status(), identity.membership().primaryBranchId(),
                assignedRoles.stream().map(RoleDefinition::name).sorted().toList(), permissions,
                actorContext.authenticationAssurance());
    }

    private TenantMembership createMembership(UUID tenantId, UserAccount user, UUID primaryBranchId, String externalReference) {
        return memberships.save(new TenantMembership(UUID.randomUUID(), tenantId, user.id(), primaryBranchId,
                normalizeNullable(externalReference, 160), clock.instant()));
    }

    private UserAccount upsertUser(String subject, String email, String displayName) {
        String normalizedSubject = required(subject, "OIDC subject", 160);
        String normalizedName = required(displayName, "Display name", 200);
        String normalizedEmail = normalizeNullable(email, 320);
        Instant now = clock.instant();
        UserAccount user = users.findByOidcSubject(normalizedSubject)
                .orElseGet(() -> users.save(new UserAccount(UUID.randomUUID(), normalizedSubject, normalizedEmail, normalizedName, now)));
        if (user.status() != UserStatus.ACTIVE) {
            throw new IdentityConflictException("User account is disabled");
        }
        user.refreshProfile(normalizedEmail, normalizedName, now);
        return user;
    }

    private List<RoleDefinition> assignInitialRoles(UUID tenantId, UUID membershipId, Collection<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return List.of();
        List<RoleDefinition> selected = new ArrayList<>();
        for (UUID roleId : Set.copyOf(roleIds)) {
            RoleDefinition role = roles.findByTenantIdAndId(tenantId, roleId)
                    .orElseThrow(() -> new IdentityNotFoundException("Role not found: " + roleId));
            assignments.save(new RoleAssignment(UUID.randomUUID(), tenantId, membershipId, role.id(),
                    AssignmentScopeKind.TENANT, null, actorContext.requireSubject(), clock.instant()));
            selected.add(role);
        }
        selected.sort(Comparator.comparing(RoleDefinition::name));
        return List.copyOf(selected);
    }

    private List<MemberView> memberViews(UUID tenantId, List<TenantMembership> content) {
        if (content.isEmpty()) return List.of();
        Map<UUID, UserAccount> userMap = users.findAllById(content.stream().map(TenantMembership::userId).toList()).stream()
                .collect(Collectors.toMap(UserAccount::id, Function.identity()));
        List<UUID> membershipIds = content.stream().map(TenantMembership::id).toList();
        List<RoleAssignment> assignmentRows = assignments.findAllByTenantIdAndMembershipIdIn(tenantId, membershipIds);
        Map<UUID, RoleDefinition> roleMap = roles.findAllByTenantIdAndIdIn(
                        tenantId, assignmentRows.stream().map(RoleAssignment::roleId).distinct().toList()).stream()
                .collect(Collectors.toMap(RoleDefinition::id, Function.identity()));
        Map<UUID, List<RoleDefinition>> memberRoles = new LinkedHashMap<>();
        for (RoleAssignment assignment : assignmentRows) {
            RoleDefinition role = roleMap.get(assignment.roleId());
            if (role != null) memberRoles.computeIfAbsent(assignment.membershipId(), ignored -> new ArrayList<>()).add(role);
        }
        return content.stream().map(membership -> {
            UserAccount user = userMap.get(membership.userId());
            if (user == null) throw new IdentityNotFoundException("User account not found for membership " + membership.id());
            List<RoleDefinition> assignedRoles = memberRoles.getOrDefault(membership.id(), List.of()).stream()
                    .sorted(Comparator.comparing(RoleDefinition::name)).toList();
            return memberView(membership, user, assignedRoles);
        }).toList();
    }

    private List<RoleDefinition> rolesForMembership(UUID tenantId, UUID membershipId) {
        List<RoleAssignment> memberAssignments = assignments.findAllByTenantIdAndMembershipId(tenantId, membershipId);
        if (memberAssignments.isEmpty()) return List.of();
        Map<UUID, RoleDefinition> found = roles.findAllByTenantIdAndIdIn(
                        tenantId, memberAssignments.stream().map(RoleAssignment::roleId).distinct().toList()).stream()
                .collect(Collectors.toMap(RoleDefinition::id, Function.identity()));
        return memberAssignments.stream().map(RoleAssignment::roleId).distinct().map(found::get)
                .filter(java.util.Objects::nonNull).sorted(Comparator.comparing(RoleDefinition::name)).toList();
    }

    private static MemberView memberView(TenantMembership membership, UserAccount user, List<RoleDefinition> assignedRoles) {
        List<RoleRef> roleRefs = assignedRoles.stream()
                .map(role -> new RoleRef(role.id(), role.systemKey(), role.name(), role.systemManaged()))
                .toList();
        return new MemberView(membership.id(), user.id(), user.oidcSubject(), user.email(), user.displayName(), user.status(),
                membership.status(), membership.primaryBranchId(), membership.externalReference(), membership.joinedAt(),
                roleRefs, membership.version());
    }

    private TenantMembership membership(UUID tenantId, UUID membershipId) {
        return memberships.findByTenantIdAndId(tenantId, membershipId)
                .orElseThrow(() -> new IdentityNotFoundException("Membership not found"));
    }

    private static int boundedSize(int size) {
        if (size < 1) return 25;
        return Math.min(size, 100);
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

    record ProvisionMembershipCommand(String subject, String email, String displayName, UUID primaryBranchId,
                                      String externalReference, Collection<UUID> roleIds) {}
    record UpdateMembershipCommand(MembershipStatus status, UUID primaryBranchId, String externalReference, long expectedVersion) {}
    record RoleRef(UUID id, String systemKey, String name, boolean systemManaged) {}
    record MemberView(UUID membershipId, UUID userId, String oidcSubject, String email, String displayName,
                      UserStatus userStatus, MembershipStatus membershipStatus, UUID primaryBranchId,
                      String externalReference, Instant joinedAt, List<RoleRef> roles, long version) {}
    record CurrentIdentityView(UUID userId, UUID membershipId, String oidcSubject, String email, String displayName,
                               UUID tenantId, MembershipStatus membershipStatus, UUID primaryBranchId,
                               List<String> roles, Set<PermissionKey> permissions,
                               com.universalplatform.security.AuthenticationAssurance authenticationAssurance) {}
}
