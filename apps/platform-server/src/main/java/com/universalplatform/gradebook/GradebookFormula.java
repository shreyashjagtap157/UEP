package com.universalplatform.gradebook;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

public record GradebookFormula(BigDecimal weightedPoints, int completedAssignments, int totalAssignments) {
    public static GradebookFormula compute(List<GradeEntry> entries) {
        return compute(entries, GradebookPolicy.standard());
    }

    /**
     * Calculates a grade deterministically after applying a versionable policy.
     * Dropped/best-of entries are selected by normalized percentage and the
     * remaining weights are renormalized so a policy cannot silently reduce a
     * learner's grade merely because an item was excluded.
     */
    public static GradebookFormula compute(List<GradeEntry> entries, GradebookPolicy policy) {
        if (entries == null || policy == null) throw new IllegalArgumentException("entries and policy are required");
        for (GradeEntry entry : entries) {
            if (entry.maxPoints() <= 0) throw new IllegalArgumentException("maxPoints must be > 0");
            if (entry.weightBasisPoints() < 0 || entry.weightBasisPoints() > 10_000) {
                throw new IllegalArgumentException("weightBasisPoints must be between 0 and 10000");
            }
            if (entry.awardedPoints() != null && (entry.awardedPoints() < 0 || entry.awardedPoints() > entry.maxPoints())) {
                throw new IllegalArgumentException("awardedPoints must be between 0 and maxPoints");
            }
        }

        int completed = (int) entries.stream().filter(e -> e.awardedPoints() != null).count();
        List<GradeEntry> graded = entries.stream().filter(e -> e.awardedPoints() != null).toList();
        List<GradeEntry> selected = select(graded, policy);

        int selectedWeight = selected.stream().mapToInt(GradeEntry::weightBasisPoints).sum();
        BigDecimal total = BigDecimal.ZERO;
        if (selectedWeight > 0) {
            for (GradeEntry entry : selected) {
                BigDecimal percentage = BigDecimal.valueOf(entry.awardedPoints())
                        .divide(BigDecimal.valueOf(entry.maxPoints()), 12, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(entry.weightBasisPoints()))
                        .divide(BigDecimal.valueOf(selectedWeight), 12, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                total = total.add(percentage);
            }
        }
        total = total.add(policy.extraCreditPercentagePoints());
        if (policy.minimumRequiredCompleted() > completed) total = BigDecimal.ZERO;
        return new GradebookFormula(total.setScale(policy.scale(), policy.roundingMode()), completed, entries.size());
    }

    private static List<GradeEntry> select(List<GradeEntry> graded, GradebookPolicy policy) {
        if (graded.isEmpty()) return graded;
        var ordered = graded.stream()
                .sorted(Comparator.comparing(GradeEntry::percentage).reversed())
                .toList();
        if (policy.bestOfCount() > 0) return ordered.subList(0, Math.min(policy.bestOfCount(), ordered.size()));
        if (policy.dropLowestCount() > 0) {
            int keep = Math.max(0, ordered.size() - policy.dropLowestCount());
            return ordered.subList(0, keep);
        }
        return graded;
    }

    public record GradeEntry(int maxPoints, int weightBasisPoints, Integer awardedPoints) {
        private BigDecimal percentage() {
            if (awardedPoints == null) return BigDecimal.ZERO;
            return BigDecimal.valueOf(awardedPoints).divide(BigDecimal.valueOf(maxPoints), 12, RoundingMode.HALF_UP);
        }
    }
}
