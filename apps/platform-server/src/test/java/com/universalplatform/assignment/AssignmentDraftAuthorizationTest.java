package com.universalplatform.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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

    @Test
    void learnerAssignmentListExcludesDraftAndArchivedAssignmentsButKeepsClosedWorkVisible() {
        UUID draftId = UUID.randomUUID();
        UUID publishedId = UUID.randomUUID();
        UUID closedId = UUID.randomUUID();
        UUID archivedId = UUID.randomUUID();
        when(enrollment.activeBatchIdsForMembership(membershipId)).thenReturn(Set.of(batchId));
        when(links.findAllByTenantIdAndBatchIdIn(tenantId, List.of(batchId))).thenReturn(List.of(
                assignmentLink(draftId), assignmentLink(publishedId), assignmentLink(closedId), assignmentLink(archivedId)));
        Assignment published = assignment(publishedId, AssignmentStatus.PUBLISHED);
        Assignment closed = assignment(closedId, AssignmentStatus.CLOSED);
        when(assignments.findAllByTenantIdAndStatusInAndIdIn(eq(tenantId),
                eq(List.of(AssignmentStatus.PUBLISHED, AssignmentStatus.CLOSED)),
                eq(List.of(draftId, publishedId, closedId, archivedId)), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(published, closed), PageRequest.of(0, 25), 2));

        AssignmentService.PageResult<AssignmentService.AssignmentView> result = service.list(0, 25);

        assertThat(result.items()).extracting(AssignmentService.AssignmentView::id)
                .containsExactly(publishedId, closedId);
        verify(assignments, never()).findAllByTenantIdAndIdIn(tenantId,
                List.of(draftId, publishedId, closedId, archivedId));
    }

    private AssignmentBatch assignmentLink(UUID targetAssignmentId) {
        return new AssignmentBatch(UUID.randomUUID(), tenantId, targetAssignmentId, batchId, NOW);
    }

    private Assignment assignment(AssignmentStatus status) {
        return assignment(assignmentId, status);
    }

    private Assignment assignment(UUID id, AssignmentStatus status) {
        return new Assignment(id, tenantId, "Scoped assignment", "Instructions", status,
                100, 10000, NOW.plusSeconds(3600), NOW.minusSeconds(300));
    }
}
