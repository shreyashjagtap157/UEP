package com.universalplatform.gradebook;

import static org.junit.jupiter.api.Assertions.*;
import java.math.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class GradebookFormulaTest {
 @Test void weightedFormulaIsNormalizedToAssignmentWeights(){
  var f=GradebookFormula.compute(List.of(new GradebookFormula.GradeEntry(100,6000,80),new GradebookFormula.GradeEntry(50,4000,25)));
  assertEquals(new BigDecimal("68.00"),f.weightedPoints()); assertEquals(2,f.completedAssignments()); assertEquals(2,f.totalAssignments());
 }
 @Test void missingGradeDoesNotCountAsComplete(){var f=GradebookFormula.compute(List.of(new GradebookFormula.GradeEntry(100,10000,null)));assertEquals(new BigDecimal("0.00"),f.weightedPoints());assertEquals(0,f.completedAssignments());}
 @Test void dropLowestRenormalizesRemainingWeight(){
  var policy = new GradebookPolicy(1,0,BigDecimal.ZERO,0,2,RoundingMode.HALF_UP);
  var f = GradebookFormula.compute(List.of(
    new GradebookFormula.GradeEntry(100,4000,100),
    new GradebookFormula.GradeEntry(100,3000,50),
    new GradebookFormula.GradeEntry(100,3000,0)), policy);
  assertEquals(new BigDecimal("78.57"), f.weightedPoints());
 }
 @Test void bestOfSelectsHighestPercentages(){
  var policy = new GradebookPolicy(0,2,BigDecimal.ZERO,0,2,RoundingMode.HALF_UP);
  var f = GradebookFormula.compute(List.of(
    new GradebookFormula.GradeEntry(100,3000,100),
    new GradebookFormula.GradeEntry(100,3000,80),
    new GradebookFormula.GradeEntry(100,4000,20)), policy);
  assertEquals(new BigDecimal("90.00"), f.weightedPoints());
 }
 @Test void extraCreditAndMinimumCompletionAreDeterministic(){
  var policy = new GradebookPolicy(0,0,new BigDecimal("5.00"),2,2,RoundingMode.HALF_UP);
  var incomplete = GradebookFormula.compute(List.of(new GradebookFormula.GradeEntry(100,10000,80)), policy);
  assertEquals(new BigDecimal("0.00"), incomplete.weightedPoints());
  var complete = GradebookFormula.compute(List.of(
    new GradebookFormula.GradeEntry(100,5000,80), new GradebookFormula.GradeEntry(100,5000,60)), policy);
  assertEquals(new BigDecimal("75.00"), complete.weightedPoints());
 }
 @Test void invalidScoresAndWeightsAreRejected(){
  assertThrows(IllegalArgumentException.class, () -> GradebookFormula.compute(List.of(new GradebookFormula.GradeEntry(0,10000,1))));
  assertThrows(IllegalArgumentException.class, () -> GradebookFormula.compute(List.of(new GradebookFormula.GradeEntry(100,10001,1))));
  assertThrows(IllegalArgumentException.class, () -> GradebookFormula.compute(List.of(new GradebookFormula.GradeEntry(100,10000,101))));
 }
 @Test void mutuallyExclusiveDropAndBestOfPoliciesAreRejected(){
  assertThrows(IllegalArgumentException.class, () -> new GradebookPolicy(1,2,BigDecimal.ZERO,0,2,RoundingMode.HALF_UP));
 }
}
