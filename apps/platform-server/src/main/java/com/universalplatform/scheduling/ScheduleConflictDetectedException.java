package com.universalplatform.scheduling;

import java.util.List;

class ScheduleConflictDetectedException extends RuntimeException {
    private final List<ScheduleConflict> conflicts;
    ScheduleConflictDetectedException(List<ScheduleConflict> conflicts) {
        super("Schedule conflicts must be resolved or explicitly overridden");
        this.conflicts = List.copyOf(conflicts);
    }
    List<ScheduleConflict> conflicts() { return conflicts; }
}
