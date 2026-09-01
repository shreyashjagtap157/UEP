package com.universalplatform.questionbank;

import com.universalplatform.audit.AuditService;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionBankService implements QuestionBankDirectory {
    private final TenantContext tenantContext;
    private final ActorContext actorContext;
    private final AuthorizationService authorization;
    private final QuestionRepository questions;
    private final QuestionVersionRepository versions;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    QuestionBankService(TenantContext tenantContext, ActorContext actorContext, AuthorizationService authorization,
                        QuestionRepository questions, QuestionVersionRepository versions, AuditService audit) {
        this.tenantContext = tenantContext; this.actorContext = actorContext; this.authorization = authorization;
        this.questions = questions; this.versions = versions; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResult<QuestionView> list(int page, int size) {
        authorization.require(PermissionKey.ASSESSMENTS_VIEW);
        var result = questions.findAllByTenantId(tenantContext.requireTenantId(),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new PageResult<>(result.getContent().stream().map(this::view).toList(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public QuestionView create(String title, QuestionType type, String difficulty, String language,
                               String payloadJson, int positiveMarks, int negativeMarks) {
        authorization.require(PermissionKey.ASSESSMENTS_MANAGE);
        validate(type, payloadJson, positiveMarks, negativeMarks);
        UUID tenantId = tenantContext.requireTenantId(); Instant now = clock.instant();
        Question q = questions.save(new Question(UUID.randomUUID(), tenantId, requireTitle(title), now));
        QuestionVersion v = versions.save(new QuestionVersion(UUID.randomUUID(), tenantId, q.id(), 1, type,
                normalize(difficulty, "medium"), language, payloadJson, positiveMarks, negativeMarks, now));
        audit.record(tenantId, actorContext.requireSubject(), "QUESTION_CREATED", "question", q.id().toString());
        return view(q, v);
    }

    @Transactional
    public QuestionView addVersion(UUID questionId, String title, QuestionType type, String difficulty, String language,
                                   String payloadJson, int positiveMarks, int negativeMarks, long expectedQuestionVersion) {
        authorization.require(PermissionKey.ASSESSMENTS_MANAGE);
        validate(type, payloadJson, positiveMarks, negativeMarks);
        UUID tenantId = tenantContext.requireTenantId();
        Question q = questions.findByTenantIdAndId(tenantId, questionId).orElseThrow(() -> new IllegalArgumentException("Question not found"));
        if (q.version() != expectedQuestionVersion) throw new IllegalStateException("Question was modified by another request");
        if (title != null && !title.isBlank()) q.update(requireTitle(title), clock.instant());
        int next = versions.findTopByTenantIdAndQuestionIdOrderByVersionNumberDesc(tenantId, questionId).map(v -> v.versionNumber() + 1).orElse(1);
        QuestionVersion v = versions.save(new QuestionVersion(UUID.randomUUID(), tenantId, questionId, next, type,
                normalize(difficulty, "medium"), language, payloadJson, positiveMarks, negativeMarks, clock.instant()));
        audit.record(tenantId, actorContext.requireSubject(), "QUESTION_VERSION_CREATED", "question", q.id().toString());
        return view(q, v);
    }

    @Transactional(readOnly = true)
    public List<QuestionVersionView> versions(UUID questionId) {
        authorization.require(PermissionKey.ASSESSMENTS_VIEW);
        UUID tenantId = tenantContext.requireTenantId();
        questions.findByTenantIdAndId(tenantId, questionId).orElseThrow(() -> new IllegalArgumentException("Question not found"));
        return versions.findAllByTenantIdAndQuestionIdOrderByVersionNumberDesc(tenantId, questionId).stream().map(this::versionView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionReference requireQuestionVersion(UUID questionVersionId) {
        UUID tenantId = tenantContext.requireTenantId();
        QuestionVersion v = versions.findByTenantIdAndId(tenantId, questionVersionId).orElseThrow(() -> new IllegalArgumentException("Question version not found"));
        Question q = questions.findByTenantIdAndId(tenantId, v.questionId()).orElseThrow(() -> new IllegalArgumentException("Question not found"));
        if (q.status() == QuestionStatus.RETIRED) throw new IllegalStateException("Retired question cannot be used for a new assessment");
        return new QuestionReference(v.id(), v.questionId(), v.type(), q.title(), v.difficulty(), v.payloadJson(), v.positiveMarks(), v.negativeMarks());
    }

    private QuestionView view(Question q) {
        var v = versions.findTopByTenantIdAndQuestionIdOrderByVersionNumberDesc(q.tenantId(), q.id()).orElseThrow();
        return view(q, v);
    }
    private QuestionView view(Question q, QuestionVersion v) { return new QuestionView(q.id(), q.title(), q.status(), q.version(), versionView(v)); }
    private QuestionVersionView versionView(QuestionVersion v) { return new QuestionVersionView(v.id(), v.versionNumber(), v.type(), v.difficulty(), v.language(), v.payloadJson(), v.positiveMarks(), v.negativeMarks(), v.createdAt()); }
    private static void validate(QuestionType type, String payloadJson, int positiveMarks, int negativeMarks) {
        if (type == null || payloadJson == null || payloadJson.isBlank()) throw new IllegalArgumentException("Question type and payload are required");
        if (positiveMarks <= 0 || negativeMarks < 0) throw new IllegalArgumentException("Invalid marks configuration");
    }
    private static String requireTitle(String title) { if (title == null || title.isBlank() || title.length() > 200) throw new IllegalArgumentException("Question title is required"); return title.trim(); }
    private static String normalize(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }

    public record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
    public record QuestionView(UUID id, String title, QuestionStatus status, long version, QuestionVersionView latestVersion) {}
    public record QuestionVersionView(UUID id, int versionNumber, QuestionType type, String difficulty, String language, String payloadJson, int positiveMarks, int negativeMarks, Instant createdAt) {}
}
