package com.universalplatform.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.universalplatform.audit.AuditService;
import com.universalplatform.enrollment.EnrollmentDirectory;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.MembershipDirectory;
import com.universalplatform.identity.MembershipStatus;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.notification.NotificationPublisher;
import com.universalplatform.questionbank.QuestionBankDirectory;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AssessmentAccessScopeTest {
    private static final Instant NOW = Instant.parse("2026-10-28T12:00:00Z");

    private final UUID tenantId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final UUID enrolledBatchId = UUID.randomUUID();
    private final UUID otherBatchId = UUID.randomUUID();
    private final UUID visibleAssessmentId = UUID.randomUUID();
    private final UUID hiddenAssessmentId = UUID.randomUUID();
    private final UUID visibleVersionId = UUID.randomUUID();
    private final UUID hiddenVersionId = UUID.randomUUID();

    @Mock TenantContext tenantContext;
    @Mock ActorContext actorContext;
    @Mock AuthorizationService auth;
    @Mock MembershipDirectory memberships;
    @Mock EnrollmentDirectory enrollment;
    @Mock QuestionBankDirectory questionBank;
    @Mock AssessmentRepository assessments;
    @Mock AssessmentVersionRepository versions;
    @Mock AssessmentQuestionRepository questions;
    @Mock AssessmentBatchRepository batches;
    @Mock AttemptRepository attempts;
    @Mock AnswerRepository answers;
    @Mock AuditService audit;
    @Mock AttemptExpiryService expiry;

    private AssessmentService service;
    private Assessment visibleAssessment;
    private Assessment hiddenAssessment;
    private AssessmentVersion visibleVersion;
    private AssessmentVersion hiddenVersion;

    @BeforeEach
    void setUp() {
        service = new AssessmentService(tenantContext, actorContext, auth, memberships, enrollment, questionBank,
                assessments, versions, questions, batches, attempts, answers, audit, expiry, new ObjectMapper());
        visibleAssessment = new Assessment(visibleAssessmentId, tenantId, "Assigned midterm", NOW.minusSeconds(500));
        visibleAssessment.publish(NOW.minusSeconds(100));
        hiddenAssessment = new Assessment(hiddenAssessmentId, tenantId, "Unassigned assessment", NOW.minusSeconds(400));
        hiddenAssessment.publish(NOW.minusSeconds(100));
        visibleVersion = version(visibleVersionId, visibleAssessmentId, 1);
        hiddenVersion = version(hiddenVersionId, hiddenAssessmentId, 1);

        lenient().when(tenantContext.requireTenantId()).thenReturn(tenantId);
        lenient().when(auth.has(PermissionKey.ASSESSMENTS_MANAGE)).thenReturn(false);
        lenient().when(memberships.current()).thenReturn(new MembershipDirectory.MembershipReference(
                memberId, UUID.randomUUID(), "student-subject", "student@example.invalid", "Student",
                null, MembershipStatus.ACTIVE));
    }

    @Test
    void learnerAssessmentListContainsOnlyPublishedAssessmentsAssignedToActiveBatches() {
        AssessmentBatch assignment = new AssessmentBatch(UUID.randomUUID(), tenantId, visibleVersionId,
                enrolledBatchId, NOW);
        when(enrollment.activeBatchIdsForMembership(memberId)).thenReturn(Set.of(enrolledBatchId));
        when(batches.findAllByTenantIdAndBatchIdIn(tenantId, Set.of(enrolledBatchId)))
                .thenReturn(List.of(assignment));
        when(versions.findAllByTenantIdAndIdIn(tenantId, Set.of(visibleVersionId)))
                .thenReturn(List.of(visibleVersion));
        when(assessments.findAllByTenantIdAndStatusAndIdIn(eq(tenantId), eq(AssessmentStatus.PUBLISHED),
                eq(Set.of(visibleAssessmentId)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(visibleAssessment), PageRequest.of(0, 25), 1));

        AssessmentService.PageResult<AssessmentService.AssessmentView> result = service.list(0, 25);

        assertThat(result.items()).extracting(AssessmentService.AssessmentView::id)
                .containsExactly(visibleAssessmentId);
        assertThat(result.totalElements()).isEqualTo(1);
        verify(assessments, never()).findAllByTenantId(eq(tenantId), any(Pageable.class));
    }

    @Test
    void learnerWithoutActiveBatchAssignmentsSeesNoAssessments() {
        when(enrollment.activeBatchIdsForMembership(memberId)).thenReturn(Set.of());

        AssessmentService.PageResult<AssessmentService.AssessmentView> result = service.list(0, 25);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isZero();
        verify(batches, never()).findAllByTenantIdAndBatchIdIn(eq(tenantId), any());
        verify(assessments, never()).findAllByTenantIdAndStatusAndIdIn(
                eq(tenantId), eq(AssessmentStatus.PUBLISHED), any(), any(Pageable.class));
    }

    @Test
    void learnerVersionListExcludesVersionsAssignedToOtherBatches() {
        when(assessments.findByTenantIdAndId(tenantId, visibleAssessmentId))
                .thenReturn(java.util.Optional.of(visibleAssessment));
        when(versions.findAllByTenantIdAndAssessmentIdOrderByVersionNumberDesc(tenantId, visibleAssessmentId))
                .thenReturn(List.of(visibleVersion));
        when(enrollment.activeBatchIdsForMembership(memberId)).thenReturn(Set.of(enrolledBatchId));
        when(batches.findAllByTenantIdAndBatchIdIn(tenantId, Set.of(enrolledBatchId)))
                .thenReturn(List.of(new AssessmentBatch(UUID.randomUUID(), tenantId, visibleVersionId,
                        enrolledBatchId, NOW)));

        List<AssessmentService.AssessmentVersionView> result = service.versions(visibleAssessmentId);

        assertThat(result).extracting(AssessmentService.AssessmentVersionView::id)
                .containsExactly(visibleVersionId);
    }

    @Test
    void learnerCannotEnumerateAnUnassignedAssessmentVersions() {
        when(assessments.findByTenantIdAndId(tenantId, hiddenAssessmentId))
                .thenReturn(java.util.Optional.of(hiddenAssessment));
        when(versions.findAllByTenantIdAndAssessmentIdOrderByVersionNumberDesc(tenantId, hiddenAssessmentId))
                .thenReturn(List.of(hiddenVersion));
        when(enrollment.activeBatchIdsForMembership(memberId)).thenReturn(Set.of(enrolledBatchId));
        when(batches.findAllByTenantIdAndBatchIdIn(tenantId, Set.of(enrolledBatchId)))
                .thenReturn(List.of(new AssessmentBatch(UUID.randomUUID(), tenantId, visibleVersionId,
                        enrolledBatchId, NOW)));

        assertThatThrownBy(() -> service.versions(hiddenAssessmentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Assessment not found");
    }

    @Test
    void assessmentManagerRetainsTenantWideAssessmentCatalogAccess() {
        when(auth.has(PermissionKey.ASSESSMENTS_MANAGE)).thenReturn(true);
        when(assessments.findAllByTenantId(eq(tenantId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(visibleAssessment, hiddenAssessment), PageRequest.of(0, 25), 2));

        AssessmentService.PageResult<AssessmentService.AssessmentView> result = service.list(0, 25);

        assertThat(result.items()).extracting(AssessmentService.AssessmentView::id)
                .containsExactly(visibleAssessmentId, hiddenAssessmentId);
        verify(enrollment, never()).activeBatchIdsForMembership(memberId);
    }

    private AssessmentVersion version(UUID id, UUID assessmentId, int versionNumber) {
        return new AssessmentVersion(id, tenantId, assessmentId, versionNumber, 3600, 1, 100, 50, false, true,
                NOW.minusSeconds(3600), NOW.plusSeconds(3600), "{}", NOW.minusSeconds(600));
    }
}
