package com.universalplatform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultRoleSeederPermissionTest {
    @Mock RoleDefinitionRepository roles;
    @Mock RolePermissionRepository permissions;

    @Test
    void studentSystemRoleIncludesAssessmentTakingPermission() {
        UUID tenantId = UUID.randomUUID();
        when(roles.findByTenantIdAndSystemKey(any(UUID.class), anyString())).thenReturn(Optional.empty());
        when(roles.save(any(RoleDefinition.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(permissions.findAllByIdRoleId(any(UUID.class))).thenReturn(List.of());

        var seeded = new DefaultRoleSeeder(roles, permissions).ensureSystemRoles(tenantId);
        RoleDefinition student = seeded.get(SystemRoleKey.STUDENT);

        assertThat(student).isNotNull();
        assertThat(student.systemKey()).isEqualTo(SystemRoleKey.STUDENT.name());
        RoleDefinition guardian = seeded.get(SystemRoleKey.GUARDIAN);
        assertThat(guardian).isNotNull();
        verify(permissions, atLeastOnce()).saveAll(argThat((Iterable<RolePermission> rows) -> {
            for (RolePermission row : rows) {
                if (row.id().roleId().equals(student.id())
                        && PermissionKey.ASSESSMENTS_TAKE.name().equals(row.id().permissionKey())) {
                    return true;
                }
            }
            return false;
        }));
        verify(permissions, never()).saveAll(argThat((Iterable<RolePermission> rows) -> {
            for (RolePermission row : rows) {
                if (row.id().roleId().equals(guardian.id())
                        && (PermissionKey.ASSESSMENTS_TAKE.name().equals(row.id().permissionKey())
                        || PermissionKey.ASSIGNMENTS_TAKE.name().equals(row.id().permissionKey())
                        || PermissionKey.REVIEW_SUBMIT.name().equals(row.id().permissionKey()))) {
                    return true;
                }
            }
            return false;
        }));
    }
}
