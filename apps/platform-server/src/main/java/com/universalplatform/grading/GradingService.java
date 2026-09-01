package com.universalplatform.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.universalplatform.assessment.AssessmentReadDirectory;
import com.universalplatform.audit.AuditService;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import com.universalplatform.questionbank.QuestionType;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradingService implements GradingDirectory {
    private final TenantContext tenant; private final ActorContext actor; private final AuthorizationService auth; private final com.universalplatform.identity.MembershipDirectory memberships;
    private final AssessmentReadDirectory assessments; private final GradeRevisionRepository revisions; private final GradeItemRepository items;
    private final AuditService audit; private final ObjectMapper mapper;
    private final Clock clock = Clock.systemUTC();

    GradingService(TenantContext tenant, ActorContext actor, AuthorizationService auth, com.universalplatform.identity.MembershipDirectory memberships, AssessmentReadDirectory assessments,
                   GradeRevisionRepository revisions, GradeItemRepository items, AuditService audit, ObjectMapper mapper) {
        this.tenant=tenant;this.actor=actor;this.auth=auth;this.memberships=memberships;this.assessments=assessments;this.revisions=revisions;this.items=items;this.audit=audit;this.mapper=mapper;
    }

    @Transactional
    @Override
    public AttemptGrade regradeAttempt(UUID attemptId, boolean publish) { return gradeAttemptInternal(attemptId, publish, GradeSource.REGRADE); }

    @Transactional
    public AttemptGrade gradeAttempt(UUID attemptId, boolean publish) { return gradeAttemptInternal(attemptId, publish, GradeSource.SYSTEM); }

    private AttemptGrade gradeAttemptInternal(UUID attemptId, boolean publish, GradeSource source) {
        auth.require(PermissionKey.GRADING_MANAGE);
        UUID tid=tenant.requireTenantId();
        var packet=assessments.requireSubmittedAttempt(tid, attemptId);
        if (!packet.submitted()) throw new IllegalStateException("Only submitted attempts can be graded");
        List<AssessmentReadDirectory.AnswerSnapshot> answers=assessments.answersForAttempt(tid, attemptId);
        int next=revisions.findTopByTenantIdAndAttemptIdOrderByRevisionNumberDesc(tid,attemptId).map(r->r.revisionNumber()+1).orElse(1);
        GradeRevision revision=new GradeRevision(UUID.randomUUID(),tid,attemptId,next,GradeRevisionStatus.GENERATED,source,0,packet.totalMarks(),clock.instant(),actor.requireSubject(),"{}");
        revision=revisions.save(revision);
        int total=0;
        for (var answer: answers) {
            var result=grade(answer.type(),answer.payloadJson(),answer.questionPayloadJson(),answer.maxMarks(),answer.negativeMarks());
            total+=result.score();
            items.save(new GradeItem(UUID.randomUUID(),tid,revision.id(),answer.answerId(),answer.assessmentQuestionId(),answer.maxMarks(),answer.negativeMarks(),result.score(),null,result.score(),clock.instant(),result.explanationJson(),result.rubricJson()));
        }
        int capped=Math.min(total,packet.totalMarks());
        revision.setAwardedMarks(capped);
        if (publish) { revisions.findAllByTenantIdAndAttemptIdOrderByRevisionNumberDesc(tid,attemptId).stream().filter(r->!r.id().equals(revision.id()) && r.status()==GradeRevisionStatus.PUBLISHED).forEach(r->{r.supersede();revisions.save(r);}); }
        revision.setStatus(publish?GradeRevisionStatus.PUBLISHED:GradeRevisionStatus.GENERATED);
        revisions.save(revision);
        audit.record(tid,actor.requireSubject(),publish?"GRADE_PUBLISHED":"GRADE_GENERATED","attempt",attemptId.toString());
        return gradeFromLatest(tid, attemptId, packet);
    }

    @Transactional
    public GradeItemView override(UUID attemptId, UUID answerId, int finalScore, String explanationJson, String rubricJson, boolean publish) {
        auth.require(PermissionKey.GRADING_MANAGE); UUID tid=tenant.requireTenantId();
        var packet=assessments.requireSubmittedAttempt(tid,attemptId); if(!packet.submitted())throw new IllegalStateException("Attempt is not submitted");
        var previous=revisions.findTopByTenantIdAndAttemptIdOrderByRevisionNumberDesc(tid,attemptId).orElseThrow(()->new IllegalStateException("Grade first"));
        var previousItem=items.findByTenantIdAndGradeRevisionIdAndAnswerId(tid,previous.id(),answerId).orElseThrow(()->new IllegalArgumentException("Answer is not graded"));
        if(finalScore < -previousItem.negativeMarks() || finalScore > previousItem.maxMarks())throw new IllegalArgumentException("Score outside question bounds");
        int next=previous.revisionNumber()+1;
        int total=previous.awardedMarks()-previousItem.finalScore()+finalScore; total=Math.min(total,packet.totalMarks());
        if(publish){previous.supersede();revisions.save(previous);}
        GradeRevision rev=new GradeRevision(UUID.randomUUID(),tid,attemptId,next,publish?GradeRevisionStatus.PUBLISHED:GradeRevisionStatus.GENERATED,GradeSource.TEACHER,total,packet.totalMarks(),clock.instant(),actor.requireSubject(),"{\"manual\":true}");
        rev=revisions.save(rev);
        for(var old: items.findAllByTenantIdAndGradeRevisionId(tid,previous.id())){
            int score=old.answerId().equals(answerId)?finalScore:old.finalScore();
            items.save(new GradeItem(UUID.randomUUID(),tid,rev.id(),old.answerId(),old.assessmentQuestionId(),old.maxMarks(),old.negativeMarks(),old.systemScore(),old.answerId().equals(answerId)?finalScore:old.teacherScore(),score,clock.instant(),old.answerId().equals(answerId)?explanationJson:old.explanationJson(),old.answerId().equals(answerId)?rubricJson:old.rubricJson()));
        }
        audit.record(tid,actor.requireSubject(),publish?"GRADE_TEACHER_OVERRIDE_PUBLISHED":"GRADE_TEACHER_OVERRIDE_SAVED","attempt",attemptId.toString());
        return items.findByTenantIdAndGradeRevisionIdAndAnswerId(tid,rev.id(),answerId).map(this::view).orElseThrow();
    }

    @Transactional
    public AttemptGrade publishLatest(UUID attemptId){auth.require(PermissionKey.GRADING_MANAGE); UUID tid=tenant.requireTenantId(); var packet=assessments.requireSubmittedAttempt(tid,attemptId); var current=revisions.findTopByTenantIdAndAttemptIdOrderByRevisionNumberDesc(tid,attemptId).orElseThrow(); revisions.findAllByTenantIdAndAttemptIdOrderByRevisionNumberDesc(tid,attemptId).stream().filter(r->!r.id().equals(current.id()) && r.status()==GradeRevisionStatus.PUBLISHED).forEach(r->{r.supersede();revisions.save(r);}); current.publish(); revisions.save(current); audit.record(tid,actor.requireSubject(),"GRADE_RESULT_PUBLISHED","attempt",attemptId.toString()); return gradeFromLatest(tid,attemptId,packet);}

    @Transactional
    public AttemptGrade regradeAttemptWithAnswerOverrides(UUID attemptId, Map<UUID,String> overrides, boolean publish, GradeSource source) {
        auth.require(PermissionKey.GRADING_MANAGE);
        UUID tid=tenant.requireTenantId(); var packet=assessments.requireSubmittedAttempt(tid,attemptId);
        if(!packet.submitted()) throw new IllegalStateException("Only submitted attempts can be graded");
        var base=assessments.answersForAttempt(tid,attemptId);
        int next=revisions.findTopByTenantIdAndAttemptIdOrderByRevisionNumberDesc(tid,attemptId).map(r->r.revisionNumber()+1).orElse(1);
        GradeRevision revision=new GradeRevision(UUID.randomUUID(),tid,attemptId,next,GradeRevisionStatus.GENERATED,source,0,packet.totalMarks(),clock.instant(),actor.requireSubject(),"{\"overrideCount\":"+overrides.size()+"}");
        revision=revisions.save(revision); int total=0;
        for(var answer:base){
            String payload=overrides.getOrDefault(answer.answerId(),answer.payloadJson());
            var result=grade(answer.type(),payload,answer.questionPayloadJson(),answer.maxMarks(),answer.negativeMarks()); total+=result.score();
            items.save(new GradeItem(UUID.randomUUID(),tid,revision.id(),answer.answerId(),answer.assessmentQuestionId(),answer.maxMarks(),answer.negativeMarks(),result.score(),null,result.score(),clock.instant(),result.explanationJson(),result.rubricJson()));
        }
        revision.setAwardedMarks(Math.min(total,packet.totalMarks())); revision.setStatus(publish?GradeRevisionStatus.PUBLISHED:GradeRevisionStatus.GENERATED); revisions.save(revision);
        audit.record(tid,actor.requireSubject(),"GRADE_REGENERATED_WITH_OVERRIDES","attempt",attemptId.toString());
        return gradeFromLatest(tid,attemptId,packet);
    }

    @Transactional(readOnly=true)
    public ProjectedAnswerGrade projectAnswer(UUID attemptId, UUID answerId, String proposedPayloadJson){
        if (proposedPayloadJson == null || proposedPayloadJson.isBlank()) throw new IllegalArgumentException("Proposed answer payload is required");
        UUID tid=tenant.requireTenantId(); var packet=assessments.requireSubmittedAttempt(tid,attemptId); var answer=assessments.answerById(tid,answerId);
        if(!answer.attemptId().equals(attemptId)) throw new IllegalArgumentException("Answer does not belong to the attempt");
        var result=grade(answer.type(),proposedPayloadJson,answer.questionPayloadJson(),answer.maxMarks(),answer.negativeMarks());
        int currentScore=revisions.findTopByTenantIdAndAttemptIdOrderByRevisionNumberDesc(tid,attemptId).flatMap(r->items.findByTenantIdAndGradeRevisionIdAndAnswerId(tid,r.id(),answerId)).map(GradeItem::finalScore).orElse(0);
        return new ProjectedAnswerGrade(answerId,currentScore,result.score(),result.score()-currentScore,result.explanationJson());
    }

    @Transactional(readOnly=true)
    public AttemptGrade currentGrade(UUID attemptId){
        UUID tid=tenant.requireTenantId();
        if (!auth.has(PermissionKey.GRADING_VIEW)) {
            auth.require(PermissionKey.ASSESSMENTS_TAKE);
            var current=memberships.current();
            if (!assessments.attemptBelongsToMembership(tid,attemptId,current.membershipId())) throw new SecurityException("Grade is not accessible to the current user");
        }
        var revisionsForAttempt=revisions.findAllByTenantIdAndAttemptIdOrderByRevisionNumberDesc(tid,attemptId);
        var rev=auth.has(PermissionKey.GRADING_VIEW) ? revisionsForAttempt.stream().findFirst().orElseThrow(()->new IllegalArgumentException("Grade not found")) : revisionsForAttempt.stream().filter(r->r.status()==GradeRevisionStatus.PUBLISHED).findFirst().orElseThrow(()->new SecurityException("Grade has not been published"));
        var p=assessments.requireSubmittedAttempt(tid,attemptId);
        return new AttemptGrade(attemptId,rev.id(),rev.revisionNumber(),rev.awardedMarks(),rev.maxMarks(),p.passMarks(),rev.awardedMarks()>=p.passMarks());
    }
    @Transactional(readOnly=true)
    public boolean hasPublishedGrade(UUID attemptId){UUID tid=tenant.requireTenantId();return revisions.findAllByTenantIdAndAttemptIdOrderByRevisionNumberDesc(tid,attemptId).stream().anyMatch(r->r.status()==GradeRevisionStatus.PUBLISHED);}
    @Transactional(readOnly=true)
    public List<GradeItemView> items(UUID attemptId){
        UUID tid=tenant.requireTenantId();
        if(!auth.has(PermissionKey.GRADING_VIEW)){auth.require(PermissionKey.ASSESSMENTS_TAKE);if(!assessments.attemptBelongsToMembership(tid,attemptId,memberships.current().membershipId()))throw new SecurityException("Grade items are not accessible to current user");}
        var latest=revisions.findTopByTenantIdAndAttemptIdOrderByRevisionNumberDesc(tid,attemptId);
        GradeRevision rev;
        if (auth.has(PermissionKey.GRADING_VIEW)) {
            rev=latest.orElseThrow(()->new IllegalArgumentException("Grade not found"));
        } else {
            rev=latest.filter(r->r.status()==GradeRevisionStatus.PUBLISHED).orElseThrow(()->new SecurityException("Grade has not been published"));
        }
        return items.findAllByTenantIdAndGradeRevisionId(tid,rev.id()).stream().map(this::view).toList();
    }
    @Transactional(readOnly=true)
    public List<GradeRevisionView> history(UUID attemptId){auth.require(PermissionKey.GRADING_VIEW);UUID tid=tenant.requireTenantId();assessments.requireSubmittedAttempt(tid,attemptId);return revisions.findAllByTenantIdAndAttemptIdOrderByRevisionNumberDesc(tid,attemptId).stream().map(r->new GradeRevisionView(r.id(),r.revisionNumber(),r.status(),r.source(),r.awardedMarks(),r.maxMarks(),r.createdAt(),r.actorSubject())).toList();}

    private GradeResult grade(QuestionType type,String answerJson,String questionJson,int maxMarks,int negativeMarks){
        try {
            JsonNode a=mapper.readTree(answerJson); JsonNode q=mapper.readTree(questionJson);
            return switch(type){
                case SINGLE_MCQ,TRUE_FALSE -> scalar(a,q,maxMarks,negativeMarks);
                case MULTIPLE_SELECTION -> multi(a,q,maxMarks,negativeMarks);
                case NUMERIC -> numeric(a,q,maxMarks,negativeMarks);
                case FILL_BLANK,SHORT_ANSWER,LONG_ANSWER,ESSAY -> text(a,q,maxMarks,negativeMarks);
                case MATCHING -> matching(a,q,maxMarks,negativeMarks);
                case ORDERING -> ordering(a,q,maxMarks,negativeMarks);
                case FILE_SUBMISSION -> new GradeResult(0,"{\"mode\":\"manual\"}","{\"required\":true}");
            };
        } catch(Exception e){ return new GradeResult(0,"{\"error\":\"invalid_answer_or_key\"}","{}"); }
    }
    private GradeResult scalar(JsonNode a,JsonNode q,int max,int negative){JsonNode expected=q.has("correctAnswer")?q.get("correctAnswer"):q.get("correctOptionId");JsonNode actual=a.has("answer")?a.get("answer"):a;if(actual==null||actual.isNull()||(actual.isTextual()&&actual.asText().trim().isEmpty()))return new GradeResult(0,"{\"answered\":false}","{}");boolean ok=expected!=null&&actual!=null&&expected.toString().equals(actual.toString());return new GradeResult(ok?max:-negative,ok?"{\"correct\":true}":"{\"correct\":false}","{}");}
    private GradeResult multi(JsonNode a,JsonNode q,int max,int negative){Set<String> expected=set(q.get("correctOptionIds"));Set<String> actual=set(a.get("answers"));if(actual.isEmpty())return new GradeResult(0,"{\"answered\":false}","{}");boolean ok=expected.equals(actual);return new GradeResult(ok?max:-negative,"{\"exactMatch\":"+ok+"}","{}");}
    private GradeResult numeric(JsonNode a,JsonNode q,int max,int negative){double expected=q.path("correctAnswer").asDouble(Double.NaN), actual=a.has("value")?a.get("value").asDouble(Double.NaN):a.asDouble(Double.NaN);if(Double.isNaN(actual))return new GradeResult(0,"{\"answered\":false}","{}");double tolerance=q.path("tolerance").asDouble(0d);boolean ok=!Double.isNaN(expected)&&Math.abs(expected-actual)<=tolerance;return new GradeResult(ok?max:-negative,"{\"tolerance\":"+tolerance+"}","{}");}
    private GradeResult text(JsonNode a,JsonNode q,int max,int negative){String actual=(a.has("answer")?a.get("answer"):a).asText("").trim();if(actual.isEmpty())return new GradeResult(0,"{\"answered\":false}","{}");Set<String> accepted=set(q.get("acceptedAnswers"));String normalized=actual.toLowerCase(Locale.ROOT);boolean ok=accepted.stream().map(v->v.toLowerCase(Locale.ROOT).trim()).anyMatch(normalized::equals);return new GradeResult(ok?max:-negative,"{\"accepted\":"+ok+"}","{}");}
    private GradeResult matching(JsonNode a,JsonNode q,int max,int negative){JsonNode expected=q.get("matches"), actual=a.get("matches");if(actual==null||actual.isNull())return new GradeResult(0,"{\"answered\":false}","{}");boolean ok=expected!=null&&expected.equals(actual);return new GradeResult(ok?max:-negative,"{\"exactMatch\":"+ok+"}","{}");}
    private GradeResult ordering(JsonNode a,JsonNode q,int max,int negative){JsonNode expected=q.get("order"),actual=a.get("order");if(actual==null||actual.isNull())return new GradeResult(0,"{\"answered\":false}","{}");boolean ok=expected!=null&&expected.equals(actual);return new GradeResult(ok?max:-negative,"{\"exactMatch\":"+ok+"}","{}");}
    private Set<String> set(JsonNode node){if(node==null||!node.isArray())return Set.of();Set<String>s=new LinkedHashSet<>();node.forEach(n->s.add(n.isTextual()?n.asText():n.toString()));return s;}
    private AttemptGrade gradeFromLatest(UUID tid, UUID attemptId, AssessmentReadDirectory.SubmittedAttempt packet){
        var rev=revisions.findTopByTenantIdAndAttemptIdOrderByRevisionNumberDesc(tid,attemptId).orElseThrow();
        return new AttemptGrade(attemptId,rev.id(),rev.revisionNumber(),rev.awardedMarks(),rev.maxMarks(),packet.passMarks(),rev.awardedMarks()>=packet.passMarks());
    }

    private GradeItemView view(GradeItem i){return new GradeItemView(i.id(),i.answerId(),i.assessmentQuestionId(),i.maxMarks(),i.negativeMarks(),i.systemScore(),i.teacherScore(),i.finalScore(),i.explanationJson(),i.rubricJson(),i.createdAt());}
    private record GradeResult(int score,String explanationJson,String rubricJson){}
    public record GradeRevisionView(UUID id,int revisionNumber,GradeRevisionStatus status,GradeSource source,int awardedMarks,int maxMarks,Instant createdAt,String actorSubject){}
    public record GradeItemView(UUID id,UUID answerId,UUID assessmentQuestionId,int maxMarks,int negativeMarks,int systemScore,Integer teacherScore,int finalScore,String explanationJson,String rubricJson,Instant createdAt){}
    public record ProjectedAnswerGrade(UUID answerId,int currentScore,int projectedScore,int delta,String explanationJson){}
}
