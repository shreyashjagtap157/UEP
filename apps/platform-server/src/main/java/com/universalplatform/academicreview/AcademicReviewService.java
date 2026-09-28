package com.universalplatform.academicreview;

import com.universalplatform.assessment.AssessmentReadDirectory;
import com.universalplatform.audit.AuditService;
import com.universalplatform.grading.GradingDirectory;
import com.universalplatform.identity.*;
import com.universalplatform.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcademicReviewService {
    private final TenantContext tenant;
    private final ActorContext actor;
    private final AuthorizationService auth;
    private final MembershipDirectory memberships;
    private final AssessmentReadDirectory assessments;
    private final GradingDirectory grading;
    private final ReviewCaseRepository cases;
    private final ReviewCommentRepository comments;
    private final ReviewImpactRepository impacts;
    private final AnswerRevisionRepository answerRevisions;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    AcademicReviewService(TenantContext tenant, ActorContext actor, AuthorizationService auth,
            MembershipDirectory memberships, AssessmentReadDirectory assessments, GradingDirectory grading,
            ReviewCaseRepository cases, ReviewCommentRepository comments, ReviewImpactRepository impacts,
            AnswerRevisionRepository answerRevisions, AuditService audit) {
        this.tenant = tenant;
        this.actor = actor;
        this.auth = auth;
        this.memberships = memberships;
        this.assessments = assessments;
        this.grading = grading;
        this.cases = cases;
        this.comments = comments;
        this.impacts = impacts;
        this.answerRevisions = answerRevisions;
        this.audit = audit;
    }

    @Transactional
    public ReviewView open(UUID attemptId, UUID targetAnswerId, ReviewType type, String subject,
            String openingArgument) {
        UUID tid = tenant.requireTenantId();
        var attempt = assessments.requireSubmittedAttempt(tid, attemptId);
        if (!attempt.submitted())
            throw new IllegalStateException("Review requires a submitted attempt");
        boolean manager = auth.has(PermissionKey.REVIEW_MANAGE);
        var current = memberships.current();
        if (!manager) {
            auth.require(PermissionKey.REVIEW_SUBMIT);
            if (!attempt.membershipId().equals(current.membershipId()))
                throw new SecurityException("Only the attempt owner can open a review");
        }
        if (type == ReviewType.ANSWER_REVISION && targetAnswerId == null)
            throw new IllegalArgumentException("Answer revision reviews require a target answer");
        if (targetAnswerId != null) {
            var target = assessments.answerById(tid, targetAnswerId);
            if (!target.attemptId().equals(attemptId))
                throw new IllegalArgumentException("Target answer does not belong to the attempt");
        }
        ReviewCase c = cases.save(new ReviewCase(UUID.randomUUID(), tid, attemptId, attempt.membershipId(),
                targetAnswerId, type, subject.trim(), openingArgument.trim(), clock.instant()));
        audit.record(tid, actor.requireSubject(), "REVIEW_OPENED", "review", c.id().toString());
        return view(c);
    }

    @Transactional(readOnly = true)
    public List<ReviewView> list(UUID attemptId) {
        UUID tid = tenant.requireTenantId();
        assessments.requireSubmittedAttempt(tid, attemptId);
        if (!auth.has(PermissionKey.REVIEW_MANAGE) && !auth.has(PermissionKey.GRADING_VIEW)
                && !auth.has(PermissionKey.AUDIT_VIEW)) {
            auth.require(PermissionKey.REVIEW_SUBMIT);
            if (!assessments.attemptBelongsToMembership(tid, attemptId, memberships.current().membershipId()))
                throw new SecurityException("Review is not accessible to current user");
        }
        return cases.findAllByTenantIdAndAttemptIdOrderByCreatedAtDesc(tid, attemptId).stream().map(this::view)
                .toList();
    }

    @Transactional
    public CommentView comment(UUID reviewId, String body) {
        UUID tid = tenant.requireTenantId();
        ReviewCase c = require(tid, reviewId);
        requireParticipantOrManager(c);
        ReviewComment x = comments.save(new ReviewComment(UUID.randomUUID(), tid, reviewId, actor.requireSubject(),
                body.trim(), clock.instant()));
        if (c.status() == ReviewStatus.OPEN)
            c.status(ReviewStatus.UNDER_REVIEW, clock.instant());
        cases.save(c);
        audit.record(tid, actor.requireSubject(), "REVIEW_COMMENTED", "review", reviewId.toString());
        return new CommentView(x.id(), x.actorSubject(), x.body(), x.createdAt());
    }

    @Transactional(readOnly = true)
    public List<CommentView> discussion(UUID reviewId) {
        UUID tid = tenant.requireTenantId();
        ReviewCase c = require(tid, reviewId);
        requireParticipantOrManager(c);
        return comments.findAllByTenantIdAndReviewCaseIdOrderByCreatedAtAsc(tid, reviewId).stream()
                .map(x -> new CommentView(x.id(), x.actorSubject(), x.body(), x.createdAt())).toList();
    }

    @Transactional
    public ImpactView impact(UUID reviewId) {
        UUID tid = tenant.requireTenantId();
        ReviewCase c = require(tid, reviewId);
        requireParticipantOrManager(c);
        var current = grading.currentGrade(c.attemptId());
        int projected = current.awardedMarks();
        String affected = "CURRENT_PUBLISHED_GRADE";
        String analysis = "{\"gradeChanged\":false}";
        if (c.targetAnswerId() != null) {
            var latest = answerRevisions
                    .findTopByTenantIdAndReviewCaseIdAndAnswerIdOrderByRevisionNumberDesc(tid, reviewId,
                            c.targetAnswerId())
                    .orElseThrow(() -> new IllegalStateException("No answer revision has been proposed"));
            var projection = grading.projectAnswer(c.attemptId(), c.targetAnswerId(), latest.proposedPayloadJson());
            int delta = projection.awardedMarks();
            projected = current.awardedMarks() + delta;
            affected = "ANSWER_REVISION";
            analysis = "{\"gradeChanged\":" + (delta != 0) + ",\"delta\":" + delta + "}";
        }
        var i = new ReviewImpact(UUID.randomUUID(), tid, reviewId, current.awardedMarks(),
                Math.min(projected, current.maxMarks()), affected, analysis, clock.instant());
        impacts.save(i);
        return new ImpactView(i.id(), i.previousScore(), i.projectedScore(), i.affectedRule(), i.analysisJson(),
                i.createdAt());
    }

    @Transactional
    public ReviewView resolve(UUID reviewId, ReviewStatus status) {
        auth.require(PermissionKey.REVIEW_MANAGE);
        if (status != ReviewStatus.RESOLVED && status != ReviewStatus.REJECTED && status != ReviewStatus.WITHDRAWN)
            throw new IllegalArgumentException("Invalid terminal review status");
        UUID tid = tenant.requireTenantId();
        ReviewCase c = require(tid, reviewId);
        if (c.status() == ReviewStatus.RESOLVED || c.status() == ReviewStatus.REJECTED
                || c.status() == ReviewStatus.WITHDRAWN)
            throw new IllegalStateException("Review is already closed");
        c.status(status, clock.instant());
        cases.save(c);
        audit.record(tid, actor.requireSubject(), "REVIEW_RESOLVED", "review", reviewId.toString());
        return view(c);
    }

    @Transactional
    public GradingDirectory.AttemptGrade regrade(UUID reviewId, boolean publish) {
        auth.require(PermissionKey.REVIEW_MANAGE);
        UUID tid = tenant.requireTenantId();
        ReviewCase c = require(tid, reviewId);
        if (c.status() == ReviewStatus.REJECTED || c.status() == ReviewStatus.WITHDRAWN)
            throw new IllegalStateException("Closed review cannot regrade");
        Map<UUID, String> overrides = new LinkedHashMap<>();
        for (var r : answerRevisions.findAllByTenantIdAndReviewCaseIdOrderByRevisionNumberDesc(tid, reviewId)) {
            if (r.status() == AnswerRevisionStatus.ACCEPTED)
                overrides.putIfAbsent(r.answerId(), r.proposedPayloadJson());
        }
        var result = overrides.isEmpty() ? grading.regradeAttempt(c.attemptId(), publish)
                : grading.regradeAttemptWithAnswerOverrides(c.attemptId(), overrides, publish,
                        com.universalplatform.grading.GradeSource.RECONCILIATION);
        audit.record(tid, actor.requireSubject(), "REVIEW_REGRADING_EXECUTED", "review", reviewId.toString());
        return result;
    }

    @Transactional
    public AnswerRevisionView proposeRevision(UUID reviewId, UUID answerId, String payloadJson) {
        UUID tid = tenant.requireTenantId();
        ReviewCase c = require(tid, reviewId);
        requireParticipantOrManager(c);
        var base = assessments.answerById(tid, answerId);
        if (!c.attemptId().equals(base.attemptId()))
            throw new IllegalArgumentException("Answer does not belong to the reviewed attempt");
        if (c.type() != ReviewType.ANSWER_REVISION)
            throw new IllegalStateException("Answer revisions require an answer-revision review");
        if (c.status() == ReviewStatus.RESOLVED || c.status() == ReviewStatus.REJECTED
                || c.status() == ReviewStatus.WITHDRAWN)
            throw new IllegalStateException("Closed review cannot accept new answer revisions");
        int next = answerRevisions
                .findTopByTenantIdAndReviewCaseIdAndAnswerIdOrderByRevisionNumberDesc(tid, reviewId, answerId)
                .map(x -> x.revisionNumber() + 1).orElse(1);
        var r = answerRevisions.save(new AnswerRevision(UUID.randomUUID(), tid, reviewId, answerId, next,
                payloadJson.trim(), actor.requireSubject(), clock.instant()));
        if (c.status() == ReviewStatus.OPEN)
            c.status(ReviewStatus.UNDER_REVIEW, clock.instant());
        cases.save(c);
        audit.record(tid, actor.requireSubject(), "ANSWER_REVISION_PROPOSED", "review", reviewId.toString());
        return revisionView(r);
    }

    @Transactional
    public AnswerRevisionView decideRevision(UUID revisionId, AnswerRevisionStatus status) {
        auth.require(PermissionKey.REVIEW_MANAGE);
        UUID tid = tenant.requireTenantId();
        var r = answerRevisions.findByTenantIdAndId(tid, revisionId)
                .orElseThrow(() -> new IllegalArgumentException("Answer revision not found"));
        var review = require(tid, r.reviewCaseId());
        if (review.status() == ReviewStatus.RESOLVED || review.status() == ReviewStatus.REJECTED
                || review.status() == ReviewStatus.WITHDRAWN)
            throw new IllegalStateException("Closed review cannot change revisions");
        if (status == AnswerRevisionStatus.ACCEPTED) {
            answerRevisions.findAllByTenantIdAndReviewCaseIdOrderByRevisionNumberDesc(tid, r.reviewCaseId()).stream()
                    .filter(x -> x.answerId().equals(r.answerId()) && !x.id().equals(r.id())
                            && x.status() == AnswerRevisionStatus.ACCEPTED)
                    .forEach(x -> {
                        x.status(AnswerRevisionStatus.REJECTED);
                        answerRevisions.save(x);
                    });
        }
        r.status(status);
        answerRevisions.save(r);
        audit.record(tid, actor.requireSubject(), "ANSWER_REVISION_DECIDED", "answer_revision", revisionId.toString());
        return revisionView(r);
    }

    @Transactional(readOnly = true)
    public List<AnswerRevisionView> revisions(UUID reviewId) {
        UUID tid = tenant.requireTenantId();
        ReviewCase c = require(tid, reviewId);
        requireParticipantOrManager(c);
        return answerRevisions.findAllByTenantIdAndReviewCaseIdOrderByRevisionNumberDesc(tid, reviewId).stream()
                .map(this::revisionView).toList();
    }

    private void requireParticipantOrManager(ReviewCase c) {
        if (auth.has(PermissionKey.REVIEW_MANAGE) || auth.has(PermissionKey.GRADING_VIEW)
                || auth.has(PermissionKey.AUDIT_VIEW))
            return;
        auth.require(PermissionKey.REVIEW_SUBMIT);
        if (!c.membershipId().equals(memberships.current().membershipId()))
            throw new SecurityException("Review is not accessible to current user");
    }

    private AnswerRevisionView revisionView(AnswerRevision r) {
        return new AnswerRevisionView(r.id(), r.answerId(), r.revisionNumber(), r.status(), r.proposedPayloadJson(),
                r.actorSubject(), r.createdAt());
    }

    private ReviewCase require(UUID tid, UUID id) {
        return cases.findByTenantIdAndId(tid, id).orElseThrow(() -> new IllegalArgumentException("Review not found"));
    }

    private ReviewView view(ReviewCase c) {
        return new ReviewView(c.id(), c.attemptId(), c.membershipId(), c.targetAnswerId(), c.type(), c.status(),
                c.subject(), c.openingArgument(), c.version(), c.createdAt(), c.updatedAt());
    }

    public record ReviewView(UUID id, UUID attemptId, UUID membershipId, UUID targetAnswerId, ReviewType type,
            ReviewStatus status, String subject, String openingArgument, int version, Instant createdAt,
            Instant updatedAt) {
    }

    public record CommentView(UUID id, String actorSubject, String body, Instant createdAt) {
    }

    public record ImpactView(UUID id, int previousScore, int projectedScore, String affectedRule, String analysisJson,
            Instant createdAt) {
    }

    public record AnswerRevisionView(UUID id, UUID answerId, int revisionNumber, AnswerRevisionStatus status,
            String proposedPayloadJson, String actorSubject, Instant createdAt) {
    }
}
