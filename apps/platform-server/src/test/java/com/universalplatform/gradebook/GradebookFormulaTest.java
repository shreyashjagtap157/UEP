package com.universalplatform.gradebook;
import static org.junit.jupiter.api.Assertions.*;
import java.math.*; import java.util.*; import org.junit.jupiter.api.Test;
class GradebookFormulaTest {
 @Test void weightedFormulaIsNormalizedToAssignmentWeights(){
  var f=GradebookFormula.compute(List.of(new GradebookFormula.GradeEntry(100,6000,80),new GradebookFormula.GradeEntry(50,4000,25)));
  assertEquals(new BigDecimal("58.00"),f.weightedPoints()); assertEquals(2,f.completedAssignments()); assertEquals(2,f.totalAssignments());
 }
 @Test void missingGradeDoesNotCountAsComplete(){var f=GradebookFormula.compute(List.of(new GradebookFormula.GradeEntry(100,10000,null)));assertEquals(new BigDecimal("0.00"),f.weightedPoints());assertEquals(0,f.completedAssignments());}
}
