package com.universalplatform.identity;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DefaultRoleSeeder {
    private static final Map<SystemRoleKey, RoleBlueprint> BLUEPRINTS = blueprints();

    private final RoleDefinitionRepository roles;
    private final RolePermissionRepository permissions;
    private final Clock clock = Clock.systemUTC();

    DefaultRoleSeeder(RoleDefinitionRepository roles, RolePermissionRepository permissions) {
        this.roles = roles;
        this.permissions = permissions;
    }

    @Transactional
    Map<SystemRoleKey, RoleDefinition> ensureSystemRoles(UUID tenantId) {
        Instant now = clock.instant();
        Map<SystemRoleKey, RoleDefinition> result = new EnumMap<>(SystemRoleKey.class);
        for (Map.Entry<SystemRoleKey, RoleBlueprint> entry : BLUEPRINTS.entrySet()) {
            SystemRoleKey key = entry.getKey();
            RoleBlueprint blueprint = entry.getValue();
            RoleDefinition role = roles.findByTenantIdAndSystemKey(tenantId, key.name())
                    .orElseGet(() -> roles.save(new RoleDefinition(
                            UUID.randomUUID(), tenantId, key, blueprint.name(), blueprint.description(), now)));
            Set<PermissionKey> current = permissions.findAllByIdRoleId(role.id()).stream()
                    .map(item -> PermissionKey.valueOf(item.id().permissionKey()))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (!current.equals(blueprint.permissions())) {
                permissions.deleteAllByIdRoleId(role.id());
                permissions.saveAll(blueprint.permissions().stream()
                        .map(permission -> new RolePermission(new RolePermissionId(role.id(), permission)))
                        .toList());
            }
            result.put(key, role);
        }
        return Map.copyOf(result);
    }

    private static Map<SystemRoleKey, RoleBlueprint> blueprints() {
        Map<SystemRoleKey, RoleBlueprint> map = new EnumMap<>(SystemRoleKey.class);
        Set<PermissionKey> all = EnumSet.allOf(PermissionKey.class);
        Set<PermissionKey> tenantAdministrator = EnumSet.allOf(PermissionKey.class);
        Set<PermissionKey> branchAdministrator = EnumSet.of(
                PermissionKey.ORGANIZATION_VIEW, PermissionKey.BRANCHES_VIEW,
                PermissionKey.USERS_VIEW, PermissionKey.ROLES_VIEW, PermissionKey.SESSIONS_VIEW);
        Set<PermissionKey> academicStaff = EnumSet.of(
                PermissionKey.ORGANIZATION_VIEW, PermissionKey.BRANCHES_VIEW, PermissionKey.USERS_VIEW);
        Set<PermissionKey> learner = EnumSet.of(PermissionKey.ORGANIZATION_VIEW, PermissionKey.BRANCHES_VIEW);
        Set<PermissionKey> support = EnumSet.of(
                PermissionKey.ORGANIZATION_VIEW, PermissionKey.BRANCHES_VIEW,
                PermissionKey.USERS_VIEW, PermissionKey.ROLES_VIEW, PermissionKey.SESSIONS_VIEW);
        Set<PermissionKey> auditor = EnumSet.of(
                PermissionKey.ORGANIZATION_VIEW, PermissionKey.BRANCHES_VIEW,
                PermissionKey.USERS_VIEW, PermissionKey.ROLES_VIEW,
                PermissionKey.SESSIONS_VIEW, PermissionKey.AUDIT_VIEW);

        map.put(SystemRoleKey.ORGANIZATION_OWNER, role("Organization Owner", "Full tenant administrative authority.", all));
        map.put(SystemRoleKey.ORGANIZATION_ADMINISTRATOR, role("Organization Administrator", "Tenant-wide administrative authority.", tenantAdministrator));
        map.put(SystemRoleKey.BRANCH_ADMINISTRATOR, role("Branch Administrator", "Branch-oriented administrative visibility; contextual write scopes are enforced by resource policies.", branchAdministrator));
        map.put(SystemRoleKey.ACADEMIC_ADMINISTRATOR, role("Academic Administrator", "Academic administration identity.", academicStaff));
        map.put(SystemRoleKey.EXAM_CONTROLLER, role("Exam Controller", "Assessment governance identity.", academicStaff));
        map.put(SystemRoleKey.FINANCE_ADMINISTRATOR, role("Finance Administrator", "Finance administration identity.", academicStaff));
        map.put(SystemRoleKey.TEACHER, role("Teacher", "Teaching identity.", academicStaff));
        map.put(SystemRoleKey.EVALUATOR, role("Evaluator", "Assessment evaluator identity.", academicStaff));
        map.put(SystemRoleKey.TEACHING_ASSISTANT, role("Teaching Assistant", "Teaching assistant identity.", academicStaff));
        map.put(SystemRoleKey.MENTOR, role("Mentor", "Mentor identity.", academicStaff));
        map.put(SystemRoleKey.STUDENT, role("Student", "Learner identity.", learner));
        map.put(SystemRoleKey.GUARDIAN, role("Guardian", "Guardian identity.", learner));
        map.put(SystemRoleKey.SUPPORT_OPERATOR, role("Support Operator", "Tenant support visibility without mutation privileges.", support));
        map.put(SystemRoleKey.AUDITOR, role("Auditor", "Read-only audit and governance visibility.", auditor));
        return Map.copyOf(map);
    }

    private static RoleBlueprint role(String name, String description, Set<PermissionKey> permissions) {
        return new RoleBlueprint(name, description, Set.copyOf(permissions));
    }

    private record RoleBlueprint(String name, String description, Set<PermissionKey> permissions) {}
}
