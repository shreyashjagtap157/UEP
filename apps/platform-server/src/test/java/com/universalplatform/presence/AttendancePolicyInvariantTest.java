package com.universalplatform.presence;

import static org.junit.jupiter.api.Assertions.*;

import com.universalplatform.liveclass.AttendancePolicy;
import org.junit.jupiter.api.Test;

class AttendancePolicyInvariantTest {
    @Test
    void percentagePolicyUsesOnlyItsPercentageThreshold() {
        assertEquals(AttendanceStatus.PRESENT,
                AttendanceCalculator.calculate(AttendancePolicy.PERCENTAGE, 2880, 3600, 1, 7500));
        assertEquals(AttendanceStatus.PARTIAL,
                AttendanceCalculator.calculate(AttendancePolicy.PERCENTAGE, 2700, 3600, 1, 8000));
    }

    @Test
    void manualPolicyDoesNotInferPresenceFromJoinDuration() {
        assertEquals(AttendanceStatus.ABSENT,
                AttendanceCalculator.calculate(AttendancePolicy.MANUAL, 3600, 3600, 1, 7500));
    }
}
