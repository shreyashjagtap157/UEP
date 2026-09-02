package com.universalplatform.presence;

import com.universalplatform.liveclass.AttendancePolicy;

/** Deterministic attendance-policy evaluation kept independent of persistence and transport. */
final class AttendanceCalculator {
    private AttendanceCalculator() {}

    static AttendanceStatus calculate(AttendancePolicy policy, long presentSeconds, long sessionDurationSeconds,
                                      int minimumAttendanceSeconds, int thresholdBasisPoints) {
        return switch (policy) {
            case MANUAL -> AttendanceStatus.ABSENT;
            case JOIN_TIME -> presentSeconds > 0 ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT;
            case MINIMUM_DURATION -> presentSeconds >= minimumAttendanceSeconds
                    ? AttendanceStatus.PRESENT
                    : presentSeconds > 0 ? AttendanceStatus.PARTIAL : AttendanceStatus.ABSENT;
            case PERCENTAGE -> {
                long ratio = sessionDurationSeconds > 0 ? presentSeconds * 10000L / sessionDurationSeconds : 0;
                yield ratio >= thresholdBasisPoints ? AttendanceStatus.PRESENT
                        : presentSeconds > 0 ? AttendanceStatus.PARTIAL : AttendanceStatus.ABSENT;
            }
        };
    }
}
