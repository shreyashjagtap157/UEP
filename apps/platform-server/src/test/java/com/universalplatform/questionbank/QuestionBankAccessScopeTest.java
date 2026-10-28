package com.universalplatform.questionbank;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.universalplatform.audit.AuditService;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class QuestionBankAccessScopeTest {
    @Mock TenantContext tenants;
    @Mock ActorContext actors;
    @Mock AuthorizationService authorization;
    @Mock QuestionRepository questions;
    @Mock QuestionVersionRepository versions;
    @Mock AuditService audit;

    private QuestionBankService service;

    @BeforeEach
    void setUp() {
        service = new QuestionBankService(tenants, actors, authorization, questions, versions, audit);
    }

    @Test
    void questionBankCatalogRequiresAssessmentManagement() {
        doThrow(new SecurityException("assessment management permission required"))
                .when(authorization).require(PermissionKey.ASSESSMENTS_MANAGE);

        assertThatThrownBy(() -> service.list(0, 25))
                .isInstanceOf(SecurityException.class)
                .hasMessage("assessment management permission required");

        verify(questions, never()).findAllByTenantId(any(UUID.class), any(Pageable.class));
        verify(tenants, never()).requireTenantId();
    }

    @Test
    void questionVersionCatalogRequiresAssessmentManagementBeforeReadingQuestion() {
        UUID questionId = UUID.randomUUID();
        doThrow(new SecurityException("assessment management permission required"))
                .when(authorization).require(PermissionKey.ASSESSMENTS_MANAGE);

        assertThatThrownBy(() -> service.versions(questionId))
                .isInstanceOf(SecurityException.class)
                .hasMessage("assessment management permission required");

        verify(questions, never()).findByTenantIdAndId(any(UUID.class), any(UUID.class));
        verify(versions, never()).findAllByTenantIdAndQuestionIdOrderByVersionNumberDesc(any(UUID.class), any(UUID.class));
        verify(tenants, never()).requireTenantId();
    }
}
