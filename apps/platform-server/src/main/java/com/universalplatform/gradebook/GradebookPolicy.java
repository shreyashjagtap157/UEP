package com.universalplatform.gradebook;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Immutable, deterministic policy for advanced grade calculation. */
public record GradebookPolicy(
        int dropLowestCount,
        int bestOfCount,
        BigDecimal extraCreditPercentagePoints,
        int minimumRequiredCompleted,
        int scale,
        RoundingMode roundingMode) {
    public GradebookPolicy {
        if (dropLowestCount < 0) throw new IllegalArgumentException("dropLowestCount must be >= 0");
        if (bestOfCount < 0) throw new IllegalArgumentException("bestOfCount must be >= 0");
        Objects.requireNonNull(extraCreditPercentagePoints, "extraCreditPercentagePoints");
        Objects.requireNonNull(roundingMode, "roundingMode");
        if (extraCreditPercentagePoints.signum() < 0) throw new IllegalArgumentException("extraCreditPercentagePoints must be >= 0");
        if (minimumRequiredCompleted < 0) throw new IllegalArgumentException("minimumRequiredCompleted must be >= 0");
        if (scale < 0 || scale > 8) throw new IllegalArgumentException("scale must be between 0 and 8");
        if (dropLowestCount > 0 && bestOfCount > 0) throw new IllegalArgumentException("dropLowestCount and bestOfCount are mutually exclusive");
    }

    public static GradebookPolicy standard() {
        return new GradebookPolicy(0, 0, BigDecimal.ZERO, 0, 2, RoundingMode.HALF_UP);
    }
}
