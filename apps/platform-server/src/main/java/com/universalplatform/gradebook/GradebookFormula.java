package com.universalplatform.gradebook;
import java.math.*;
public record GradebookFormula(BigDecimal weightedPoints,int completedAssignments,int totalAssignments) {
    public static GradebookFormula compute(java.util.List<GradeEntry> entries){BigDecimal total=BigDecimal.ZERO;int completed=0;for(GradeEntry e:entries){if(e.awardedPoints()!=null){completed++;total=total.add(BigDecimal.valueOf(e.awardedPoints()).divide(BigDecimal.valueOf(e.maxPoints()),8,RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(e.weightBasisPoints())).movePointLeft(2));}}return new GradebookFormula(total.setScale(2,RoundingMode.HALF_UP),completed,entries.size());}
    public record GradeEntry(int maxPoints,int weightBasisPoints,Integer awardedPoints){}
}
