package com.universalplatform.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.universalplatform.audit.AuditService;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class LearningServiceIntegrityTest {
    private final UUID tenantId = UUID.randomUUID();

    @Mock TenantContext tenant;
    @Mock ActorContext actor;
    @Mock AuthorizationService authorization;
    @Mock AuditService audit;
    @Mock JdbcTemplate jdbc;

    private LearningService service;

    @BeforeEach
    void setUp() {
        service = new LearningService(tenant, actor, authorization, audit, jdbc);
        when(tenant.requireTenantId()).thenReturn(tenantId);
    }

    @Test
    void mappingTargetsAreResolvedOnlyThroughTheExplicitAllowlist() {
        assertThat(LearningService.mappingTargetTable("CLASS")).isEqualTo("class_session");
        assertThat(LearningService.mappingTargetTable("QUESTION")).isEqualTo("question");
        assertThat(LearningService.mappingTargetTable("ASSIGNMENT")).isEqualTo("assignment");
        assertThat(LearningService.mappingTargetTable("COURSE")).isEqualTo("course");
        assertThat(LearningService.mappingTargetTable("CREDENTIAL")).isEqualTo("credential");
        assertThatThrownBy(() -> LearningService.mappingTargetTable("TENANT_MEMBERSHIP"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported learning target type");
        assertThatThrownBy(() -> LearningService.mappingTargetTable(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported learning target type");
    }

    @Test
    void mappingRejectsTargetThatDoesNotExistInTheActiveTenant() {
        UUID outcomeId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("learning_outcome")) return 1L;
            if (sql.contains("course")) return 0L;
            return 0L;
        });

        assertThatThrownBy(() -> service.mapOutcome("COURSE", targetId, outcomeId, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("course not found");

        verify(jdbc, never()).update(startsWith("insert into learning_outcome_mapping"), any(Object[].class));
        verify(audit, never()).record(any(UUID.class), any(String.class), any(String.class), any(String.class), any(String.class));
    }

    @Test
    void masteryRejectsCompetencyOwnedByAnotherOutcome() {
        UUID outcomeId = UUID.randomUUID();
        UUID competencyId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();

        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("learning_outcome")) return 1L;
            if (sql.contains("tenant_membership")) return 1L;
            if (sql.contains("competency")) return 0L;
            return 0L;
        });

        assertThatThrownBy(() -> service.recordMastery(outcomeId, competencyId, membershipId, 0.8, "test evidence"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("competency not found for learning outcome");

        verify(jdbc, never()).update(startsWith("insert into competency_mastery"), any(Object[].class));
    }


    @Test
    void progressRejectsNonFiniteValuesBeforePersisting() {
        UUID pathId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();

        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);

        assertThatThrownBy(() -> service.recordProgress(pathId, membershipId, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("progress must be finite and between 0 and 1");

        verify(jdbc, never()).update(startsWith("insert into learning_path_progress"), any(Object[].class));
        verify(audit, never()).record(any(UUID.class), any(String.class), any(String.class), any(String.class), any(String.class));
    }

    @Test
    void successfulProgressUpdateIsAudited() {
        UUID pathId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(actor.requireSubject()).thenReturn("test-actor");

        service.recordProgress(pathId, membershipId, 0.5);

        verify(jdbc).update(startsWith("insert into learning_path_progress"), any(Object[].class));
        verify(audit).record(tenantId, "test-actor", "LEARNING_PATH_PROGRESS_RECORDED",
                "learning_path_progress", pathId + ":" + membershipId);
    }

    @Test
    void successfulMasteryUpdateIsAudited() {
        UUID outcomeId = UUID.randomUUID();
        UUID competencyId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(actor.requireSubject()).thenReturn("test-actor");

        service.recordMastery(outcomeId, competencyId, membershipId, 0.9, "verified evidence");

        verify(jdbc).update(startsWith("insert into competency_mastery"), any(Object[].class));
        verify(audit).record(tenantId, "test-actor", "COMPETENCY_MASTERY_RECORDED",
                "competency_mastery", competencyId + ":" + membershipId);
    }

    @Test
    void masteryRejectsNonFiniteValuesBeforePersisting() {
        UUID outcomeId = UUID.randomUUID();
        UUID competencyId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();

        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("learning_outcome")) return 1L;
            if (sql.contains("tenant_membership")) return 1L;
            if (sql.contains("competency")) return 1L;
            return 0L;
        });

        assertThatThrownBy(() -> service.recordMastery(outcomeId, competencyId, membershipId, Double.NaN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("mastery must be finite and between 0 and 1");
        verify(jdbc, never()).update(startsWith("insert into competency_mastery"), any(Object[].class));
    }
}
