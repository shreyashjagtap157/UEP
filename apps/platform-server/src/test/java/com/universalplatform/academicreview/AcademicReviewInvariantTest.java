package com.universalplatform.academicreview;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AcademicReviewInvariantTest {
    @Test
    void terminalReviewsCannotAcceptNewEvidence() {
        assertTrue(isTerminal(ReviewStatus.RESOLVED));
        assertTrue(isTerminal(ReviewStatus.REJECTED));
        assertTrue(isTerminal(ReviewStatus.WITHDRAWN));
        assertFalse(isTerminal(ReviewStatus.OPEN));
        assertFalse(isTerminal(ReviewStatus.UNDER_REVIEW));
    }

    private static boolean isTerminal(ReviewStatus status) {
        return status == ReviewStatus.RESOLVED || status == ReviewStatus.REJECTED || status == ReviewStatus.WITHDRAWN;
    }
}
