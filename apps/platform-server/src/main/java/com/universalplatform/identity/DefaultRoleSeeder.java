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
                PermissionKey.USERS_VIEW, PermissionKey.ROLES_VIEW, PermissionKey.SESSIONS_VIEW,
                PermissionKey.ACADEMICS_VIEW, PermissionKey.CURRICULUM_VIEW,
                PermissionKey.SCHEDULE_VIEW, PermissionKey.SCHEDULE_MANAGE,
                PermissionKey.ANNOUNCEMENTS_VIEW, PermissionKey.ANNOUNCEMENTS_MANAGE,
                PermissionKey.CONTENT_VIEW, PermissionKey.ASSESSMENTS_VIEW, PermissionKey.ASSESSMENTS_MANAGE);
        Set<PermissionKey> academicAdministrator = EnumSet.of(
                PermissionKey.ORGANIZATION_VIEW, PermissionKey.BRANCHES_VIEW, PermissionKey.USERS_VIEW,
                PermissionKey.ACADEMICS_VIEW, PermissionKey.ACADEMICS_MANAGE,
                PermissionKey.CURRICULUM_VIEW, PermissionKey.CURRICULUM_MANAGE,
                PermissionKey.ENROLLMENTS_VIEW, PermissionKey.ENROLLMENTS_MANAGE,
                PermissionKey.TEACHING_ASSIGNMENTS_VIEW, PermissionKey.TEACHING_ASSIGNMENTS_MANAGE,
                PermissionKey.SCHEDULE_VIEW, PermissionKey.SCHEDULE_MANAGE,
                PermissionKey.ANNOUNCEMENTS_VIEW, PermissionKey.ANNOUNCEMENTS_MANAGE,
                PermissionKey.CONTENT_VIEW, PermissionKey.CONTENT_MANAGE, PermissionKey.ASSESSMENTS_VIEW, PermissionKey.ASSESSMENTS_MANAGE);
        Set<PermissionKey> academicReadOnly = EnumSet.of(
                PermissionKey.ORGANIZATION_VIEW, PermissionKey.BRANCHES_VIEW,
                PermissionKey.ACADEMICS_VIEW, PermissionKey.CURRICULUM_VIEW,
                PermissionKey.SCHEDULE_VIEW, PermissionKey.ANNOUNCEMENTS_VIEW, PermissionKey.CONTENT_VIEW, PermissionKey.ASSESSMENTS_VIEW);
        Set<PermissionKey> teacher = EnumSet.of(
                PermissionKey.ORGANIZATION_VIEW, PermissionKey.BRANCHES_VIEW,
                PermissionKey.ACADEMICS_VIEW, PermissionKey.CURRICULUM_VIEW,
                PermissionKey.SCHEDULE_VIEW, PermissionKey.ANNOUNCEMENTS_VIEW, PermissionKey.CONTENT_VIEW, PermissionKey.CONTENT_MANAGE, PermissionKey.ASSESSMENTS_VIEW, PermissionKey.ASSESSMENTS_MANAGE);
        Set<PermissionKey> learner = EnumSet.of(
                PermissionKey.ORGANIZATION_VIEW, PermissionKey.BRANCHES_VIEW,
                PermissionKey.ACADEMICS_VIEW, PermissionKey.CURRICULUM_VIEW,
                PermissionKey.SCHEDULE_VIEW, PermissionKey.ANNOUNCEMENTS_VIEW, PermissionKey.CONTENT_VIEW, PermissionKey.ASSESSMENTS_VIEW);
        Set<PermissionKey> support = EnumSet.of(
                PermissionKey.ORGANIZATION_VIEW, PermissionKey.BRANCHES_VIEW,
                PermissionKey.USERS_VIEW, PermissionKey.ROLES_VIEW, PermissionKey.SESSIONS_VIEW,
                PermissionKey.ACADEMICS_VIEW, PermissionKey.CURRICULUM_VIEW,
                PermissionKey.SCHEDULE_VIEW, PermissionKey.ANNOUNCEMENTS_VIEW, PermissionKey.CONTENT_VIEW,
                PermissionKey.NOTIFICATION_OPERATIONS_VIEW);
        Set<PermissionKey> auditor = EnumSet.of(
                PermissionKey.ORGANIZATION_VIEW, PermissionKey.BRANCHES_VIEW,
                PermissionKey.USERS_VIEW, PermissionKey.ROLES_VIEW, PermissionKey.SESSIONS_VIEW,
                PermissionKey.ACADEMICS_VIEW, PermissionKey.CURRICULUM_VIEW,
                PermissionKey.ENROLLMENTS_VIEW, PermissionKey.TEACHING_ASSIGNMENTS_VIEW,
                PermissionKey.SCHEDULE_VIEW, PermissionKey.ANNOUNCEMENTS_VIEW,
                PermissionKey.NOTIFICATION_OPERATIONS_VIEW, PermissionKey.CONTENT_VIEW, PermissionKey.AUDIT_VIEW);

        map.put(SystemRoleKey.ORGANIZATION_OWNER, role("Organization Owner", "Full tenant administrative authority.", all));
        map.put(SystemRoleKey.ORGANIZATION_ADMINISTRATOR, role("Organization Administrator", "Tenant-wide administrative authority.", tenantAdministrator));
        map.put(SystemRoleKey.BRANCH_ADMINISTRATOR, role("Branch Administrator", "Branch-oriented administrative visibility; contextual write scopes are enforced by resource policies.", branchAdministrator));
        map.put(SystemRoleKey.ACADEMIC_ADMINISTRATOR, role("Academic Administrator", "Academic structure, curriculum, enrollment, and teaching assignment administration.", academicAdministrator));
        map.put(SystemRoleKey.EXAM_CONTROLLER, role("Exam Controller", "Assessment governance identity with assessment construction authority.", EnumSet.of(
                PermissionKey.ORGANIZATION_VIEW, PermissionKey.BRANCHES_VIEW, PermissionKey.ACADEMICS_VIEW, PermissionKey.CURRICULUM_VIEW,
                PermissionKey.ASSESSMENTS_VIEW, PermissionKey.ASSESSMENTS_MANAGE, PermissionKey.SCHEDULE_VIEW, PermissionKey.ANNOUNCEMENTS_VIEW, PermissionKey.CONTENT_VIEW)));
        map.put(SystemRoleKey.FINANCE_ADMINISTRATOR, role("Finance Administrator", "Finance administration identity with academic catalog visibility.", academicReadOnly));
        map.put(SystemRoleKey.TEACHER, role("Teacher", "Teaching identity with academic catalog visibility; roster access is assignment-scoped.", teacher));
        map.put(SystemRoleKey.EVALUATOR, role("Evaluator", "Assessment evaluator identity with academic catalog visibility.", academicReadOnly));
        map.put(SystemRoleKey.TEACHING_ASSISTANT, role("Teaching Assistant", "Teaching assistant identity with academic catalog visibility.", teacher));
        map.put(SystemRoleKey.MENTOR, role("Mentor", "Mentor identity with academic catalog visibility.", teacher));
        map.put(SystemRoleKey.STUDENT, role("Student", "Learner identity with academic catalog visibility and self-scoped enrollment access.", learner));
        map.put(SystemRoleKey.GUARDIAN, role("Guardian", "Guardian identity with academic catalog visibility; learner data remains relationship-scoped.", learner));
        map.put(SystemRoleKey.SUPPORT_OPERATOR, role("Support Operator", "Tenant support visibility without academic mutation privileges.", support));
        map.put(SystemRoleKey.AUDITOR, role("Auditor", "Read-only governance visibility across identity and academic records.", auditor));
        return Map.copyOf(map);
    }

    private static RoleBlueprint role(String name, String description, Set<PermissionKey> permissions) {
        return new RoleBlueprint(name, description, Set.copyOf(permissions));
    }

    private record RoleBlueprint(String name, String description, Set<PermissionKey> permissions) {}
}
