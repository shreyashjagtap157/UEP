package com.universalplatform.scheduling;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ScheduleConflictOverrideRepository extends JpaRepository<ScheduleConflictOverride, UUID> {}
