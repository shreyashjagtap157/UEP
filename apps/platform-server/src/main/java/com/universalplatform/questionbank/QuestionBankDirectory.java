package com.universalplatform.questionbank;

import java.util.UUID;

public interface QuestionBankDirectory {
    QuestionReference requireQuestionVersion(UUID questionVersionId);

    record QuestionReference(UUID questionVersionId, UUID questionId, QuestionType type, String title,
                             String difficulty, String payloadJson, int positiveMarks, int negativeMarks) {}
}
