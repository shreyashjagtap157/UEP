package com.universalplatform.tenancy;

record TenantStats(
        int totalTeachers,
        int totalStudents,
        int activeSessions,
        int totalAssessments,
        int totalAnnouncements,
        double utilizationPercentage
) {}
