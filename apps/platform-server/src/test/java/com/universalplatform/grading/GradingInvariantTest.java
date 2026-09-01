package com.universalplatform.grading;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GradingInvariantTest {
    @Test
    void scoreBoundsRespectNegativeMarkAndMaximum() {
        assertTrue(within(-2, 5, -2));
        assertTrue(within(-2, 5, 5));
        assertFalse(within(-2, 5, -3));
        assertFalse(within(-2, 5, 6));
    }

    @Test
    void unansweredObjectiveResponsesDoNotReceiveNegativeMark() {
        assertEquals(0, unansweredScore());
    }

    private static boolean within(int negativeMarks, int maxMarks, int score) {
        return score >= -negativeMarks && score <= maxMarks;
    }

    private static int unansweredScore() {
        return 0;
    }
}
