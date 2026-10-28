package com.universalplatform.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.universalplatform.audit.AuditService;
import com.universalplatform.content.ContentDirectory;
import com.universalplatform.enrollment.EnrollmentDirectory;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.MembershipDirectory;
import com.universalplatform.identity.MembershipStatus;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignmentDraftAuthorizationTest {
    private static final Instant NOW = Instant.parse("2026-10-28T12:30:00Z");

    private final UUID tenantId = UUID.randomUUID();
    private final UUID membershipId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID batchId = UUID.randomUUID();

    @Mock TenantContext tenant;
    @Mock ActorContext actor;
    @Mock AuthorizationService auth;
    @Mock MembershipDirectory memberships;
    @Mock EnrollmentDirectory enrollment;
    @Mock ContentDirectory content;
    @Mock AssignmentRepository assignments;
    @Mock AssignmentBatchRepository links;
    @Mock AssignmentSubmissionRepository submissions;
    @Mock RubricRepository rubrics;
    @Mock AuditService audit;

    private AssignmentService service;

    @BeforeEach
    void setUp() {
        service = new AssignmentService(tenant, actor, auth, memberships, enrollment, content, assignments,
                links, submissions, rubrics, audit);
        lenient().when(tenant.requireTenantId()).thenReturn(tenantId);
        lenient().when(memberships.current()).thenReturn(new MembershipDirectory.MembershipReference(
                membershipId, UUID.randomUUID(), "learner-subject", "learner@example.invalid", "Learner",
                null, MembershipStatus.ACTIVE));
    }

    @Test
    void draftCannotBeSavedForAnUnpublishedAssignment() {
        Assignment draft = assignment(AssignmentStatus.DRAFT);
        when(assignments.findByTenantIdAndId(tenantId, assignmentId)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.saveDraft(assignmentId, "my answer", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Assignment is not accepting submissions");

        verify(links, never()).findAllByTenantIdAndAssignmentId(tenantId, assignmentId);
        verify(submissions, never()).save(any(AssignmentSubmission.class));
    }

    @Test
    void draftCannotBeSavedForAnAssignmentOutsideTheLearnersActiveBatches() {
        Assignment published = assignment(AssignmentStatus.PUBLISHED);
        when(assignments.findByTenantIdAndId(tenantId, assignmentId)).thenReturn(Optional.of(published));
        when(links.findAllByTenantIdAndAssignmentId(tenantId, assignmentId)).thenReturn(List.of(
                new AssignmentBatch(UUID.randomUUID(), tenantId, assignmentId, batchId, NOW)));
        when(enrollment.activeLearnerMembershipIdsForBatch(batchId)).thenReturn(Set.of());

        assertThatThrownBy(() -> service.saveDraft(assignmentId, "my answer", null))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Assignment is not assigned to this learner");

        verify(submissions, never()).save(any(AssignmentSubmission.class));
    }

    @Test
    void draftCanBeSavedForAPublishedAssignmentInTheLearnersActiveBatch() {
        Assignment published = assignment(AssignmentStatus.PUBLISHED);
        when(assignments.findByTenantIdAndId(tenantId, assignmentId)).thenReturn(Optional.of(published));
        when(links.findAllByTenantIdAndAssignmentId(tenantId, assignmentId)).thenReturn(List.of(
                new AssignmentBatch(UUID.randomUUID(), tenantId, assignmentId, batchId, NOW)));
        when(enrollment.activeLearnerMembershipIdsForBatch(batchId)).thenReturn(Set.of(membershipId));
        when(submissions.findTopByTenantIdAndAssignmentIdAndMembershipIdOrderByAttemptNumberDesc(
                tenantId, assignmentId, membershipId)).thenReturn(Optional.empty());
        when(submissions.save(any(AssignmentSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssignmentService.SubmissionView saved = service.saveDraft(assignmentId, "my answer", null);

        assertThat(saved.assignmentId()).isEqualTo(assignmentId);
        assertThat(saved.status()).isEqualTo(SubmissionStatus.DRAFT);
        assertThat(saved.textBody()).isEqualTo("my answer");
    }

    private Assignment assignment(AssignmentStatus status) {
        return new Assignment(assignmentId, tenantId, "Scoped assignment", "Instructions", status,
                100, 10000, NOW.plusSeconds(3600), NOW.minusSeconds(300));
    }
}
