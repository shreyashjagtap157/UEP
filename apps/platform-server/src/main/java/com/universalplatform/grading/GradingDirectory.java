package com.universalplatform.grading;

import java.util.*;

public interface GradingDirectory {
    AttemptGrade currentGrade(UUID attemptId);
    boolean hasPublishedGrade(UUID attemptId);
    AttemptGrade regradeAttempt(UUID attemptId, boolean publish);
    AttemptGrade regradeAttemptWithAnswerOverrides(UUID attemptId, Map<UUID,String> overrides, boolean publish, GradeSource source);
    ProjectedAnswerGrade projectAnswer(UUID attemptId, UUID answerId, String proposedPayloadJson);
    record AttemptGrade(UUID attemptId, UUID gradeRevisionId, int revisionNumber, int awardedMarks,
                        int maxMarks, int passMarks, boolean passed) {}
}
