package com.universalplatform.grading;

import java.util.*;

public interface GradingDirectory {
    AttemptGrade currentGrade(UUID attemptId);

    AttemptGrade regradeAttempt(UUID attemptId, boolean publish);

    AttemptGrade regradeAttemptWithAnswerOverrides(UUID attemptId, Map<UUID, String> overrides, boolean publish,
            GradeSource source);

    ProjectedAnswerGrade projectAnswer(UUID attemptId, UUID answerId, String proposedPayloadJson);

    /** Latest revision's final score of a single graded item across all revisions of the attempt (0 when ungraded). */
    int answerScore(UUID attemptId, UUID answerId);

    record ProjectedAnswerGrade(UUID answerId, int awardedMarks, int maxMarks, boolean evaluated) {
    }

    record AttemptGrade(UUID attemptId, UUID gradeRevisionId, int revisionNumber, int awardedMarks,
            int maxMarks, int passMarks, boolean passed) {
    }
}
