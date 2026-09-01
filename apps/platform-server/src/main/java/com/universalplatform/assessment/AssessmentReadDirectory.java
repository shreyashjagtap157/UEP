package com.universalplatform.assessment;
import java.time.Instant; import java.util.*;
public interface AssessmentReadDirectory {
    SubmittedAttempt requireSubmittedAttempt(UUID tenantId, UUID attemptId);
    List<AnswerSnapshot> answersForAttempt(UUID tenantId, UUID attemptId);
    boolean attemptBelongsToMembership(UUID tenantId, UUID attemptId, UUID membershipId);
    AnswerSnapshot answerById(UUID tenantId, UUID answerId);
    record SubmittedAttempt(UUID attemptId,UUID membershipId,int totalMarks,int passMarks,boolean submitted){}
    record AnswerSnapshot(UUID answerId,UUID attemptId,UUID assessmentQuestionId,com.universalplatform.questionbank.QuestionType type,String payloadJson,String questionPayloadJson,int maxMarks,int negativeMarks){}
}
