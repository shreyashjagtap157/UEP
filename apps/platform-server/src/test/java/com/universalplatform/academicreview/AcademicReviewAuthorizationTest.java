package com.universalplatform.academicreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.universalplatform.assessment.AssessmentReadDirectory;
import com.universalplatform.audit.AuditService;
import com.universalplatform.enrollment.EnrollmentDirectory;
import com.universalplatform.grading.GradingDirectory;
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
class AcademicReviewAuthorizationTest {
    private static final Instant NOW = Instant.parse("2026-10-28T13:00:00Z");

    private final UUID tenantId = UUID.randomUUID();
    private final UUID reviewerMembershipId = UUID.randomUUID();
    private final UUID learnerMembershipId = UUID.randomUUID();
    private final UUID reviewerBatchId = UUID.randomUUID();
    private final UUID learnerBatchId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();
    private final UUID reviewId = UUID.randomUUID();
    private final UUID revisionId = UUID.randomUUID();
    private final UUID answerId = UUID.randomUUID();

    @Mock TenantContext tenant;
    @Mock ActorContext actor;
    @Mock AuthorizationService auth;
    @Mock MembershipDirectory memberships;
    @Mock EnrollmentDirectory enrollment;
    @Mock AssessmentReadDirectory assessments;
    @Mock GradingDirectory grading;
    @Mock ReviewCaseRepository cases;
    @Mock ReviewCommentRepository comments;
    @Mock ReviewImpactRepository impacts;
    @Mock AnswerRevisionRepository answerRevisions;
    @Mock AuditService audit;

    private AcademicReviewService service;
    private ReviewCase review;
    private AnswerRevision revision;

    @BeforeEach
    void setUp() {
        service = new AcademicReviewService(tenant, actor, auth, memberships, enrollment, assessments, grading,
                cases, comments, impacts, answerRevisions, audit);
        review = new ReviewCase(reviewId, tenantId, attemptId, learnerMembershipId, answerId,
                ReviewType.ANSWER_REVISION, "Incorrect answer", "Please reconsider this answer", NOW);
        revision = new AnswerRevision(revisionId, tenantId, reviewId, answerId, 1,
                "{\"answer\":\"revised\"}", "learner", NOW);

        lenient().when(tenant.requireTenantId()).thenReturn(tenantId);
        lenient().when(actor.requireSubject()).thenReturn("reviewer-subject");
        lenient().when(memberships.current()).thenReturn(new MembershipDirectory.MembershipReference(
                reviewerMembershipId, UUID.randomUUID(), "reviewer-subject", "reviewer@example.invalid",
                "Reviewer", null, MembershipStatus.ACTIVE));
        lenient().when(cases.findByTenantIdAndId(tenantId, reviewId)).thenReturn(Optional.of(review));
        lenient().when(answerRevisions.findByTenantIdAndId(tenantId, revisionId)).thenReturn(Optional.of(revision));
        lenient().when(assessments.requireSubmittedAttempt(tenantId, attemptId))
                .thenReturn(new AssessmentReadDirectory.SubmittedAttempt(attemptId, learnerMembershipId, 100, 50, true));
        lenient().when(auth.currentScope(PermissionKey.REVIEW_MANAGE))
                .thenReturn(new AuthorizationService.AccessScope(false, Set.of(reviewerBatchId)));
        lenient().when(enrollment.activeBatchIdsForMembership(reviewerMembershipId)).thenReturn(Set.of(reviewerBatchId));
    }

    @Test
    void reviewerCannotDecideRevisionForLearnerOutsideAssignedBatches() {
        when(enrollment.activeBatchIdsForMembership(learnerMembershipId)).thenReturn(Set.of(learnerBatchId));

        assertThatThrownBy(() -> service.decideRevision(revisionId, AnswerRevisionStatus.ACCEPTED))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Review is outside the manager's assigned batches");

        verify(answerRevisions, never()).save(any(AnswerRevision.class));
        verify(cases, never()).save(any(ReviewCase.class));
        verify(audit, never()).record(any(UUID.class), any(String.class), any(String.class), any(String.class), any(String.class));
    }

    @Test
    void reviewerCanDecideRevisionWhenLearnerSharesAnAssignedBatch() {
        when(enrollment.activeBatchIdsForMembership(learnerMembershipId)).thenReturn(Set.of(reviewerBatchId));
        when(answerRevisions.findAllByTenantIdAndReviewCaseIdOrderByRevisionNumberDesc(tenantId, reviewId))
                .thenReturn(List.of(revision));
        when(answerRevisions.save(revision)).thenReturn(revision);

        AcademicReviewService.AnswerRevisionView result =
                service.decideRevision(revisionId, AnswerRevisionStatus.ACCEPTED);

        assertThat(result.status()).isEqualTo(AnswerRevisionStatus.ACCEPTED);
        verify(answerRevisions).save(revision);
        verify(audit).record(tenantId, "reviewer-subject", "ANSWER_REVISION_DECIDED", "answer_revision", revisionId.toString());
    }
}
