package com.universalplatform.gradebook;

import static org.junit.jupiter.api.Assertions.*;
import java.math.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.universalplatform.assignment.AssignmentDirectory;
import com.universalplatform.enrollment.EnrollmentDirectory;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.MembershipDirectory;
import com.universalplatform.identity.MembershipStatus;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.identity.SystemRoleKey;
import com.universalplatform.security.TenantContext;

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

@ExtendWith(MockitoExtension.class)
class GradebookAuthorizationTest {
 @Mock TenantContext tenant; @Mock AuthorizationService auth; @Mock MembershipDirectory memberships;
 @Mock EnrollmentDirectory enrollment; @Mock AssignmentDirectory assignments;
 private final UUID viewer=UUID.randomUUID(), learner=UUID.randomUUID(), batch=UUID.randomUUID(), branch=UUID.randomUUID();
 private GradebookService service;
 @BeforeEach void setup(){
  service=new GradebookService(tenant,auth,memberships,enrollment,assignments);
  when(memberships.current()).thenReturn(new MembershipDirectory.MembershipReference(viewer,UUID.randomUUID(),"viewer","viewer@example.invalid","Viewer",null,MembershipStatus.ACTIVE));
  when(enrollment.requireBatch(batch)).thenReturn(new EnrollmentDirectory.BatchReference(batch,null,null,null,branch,"B-1","Batch",null,null,null));
  lenient().when(auth.currentScope(PermissionKey.GRADEBOOK_VIEW)).thenReturn(new AuthorizationService.AccessScope(false,Set.of()));
  lenient().when(assignments.gradebookAssignments(batch,learner)).thenReturn(List.of());
 }
 @Test void studentCannotReadAnotherLearnersGradebook(){
  when(enrollment.activeBatchIdsForMembership(learner)).thenReturn(Set.of(batch));
  when(memberships.hasSystemRole(viewer,SystemRoleKey.STUDENT)).thenReturn(true);
  SecurityException ex=assertThrows(SecurityException.class,()->service.learner(batch,learner));
  assertEquals("Students may only view their own gradebook",ex.getMessage());
  verify(assignments,never()).gradebookAssignments(batch,learner);
 }
 @Test void guardianCannotReadAnotherLearnersGradebookWithoutVerifiedRelationship(){
  when(enrollment.activeBatchIdsForMembership(learner)).thenReturn(Set.of(batch));
  when(memberships.hasSystemRole(viewer,SystemRoleKey.GUARDIAN)).thenReturn(true);
  SecurityException ex=assertThrows(SecurityException.class,()->service.learner(batch,learner));
  assertEquals("Guardian gradebook access requires a verified learner relationship",ex.getMessage());
  verify(assignments,never()).gradebookAssignments(batch,learner);
 }
 @Test void staffOutsideLearnerBatchCannotReadGradebook(){
  when(enrollment.activeBatchIdsForMembership(learner)).thenReturn(Set.of(batch));
  when(enrollment.activeBatchIdsForMembership(viewer)).thenReturn(Set.of(UUID.randomUUID()));
  SecurityException ex=assertThrows(SecurityException.class,()->service.learner(batch,learner));
  assertEquals("Learner is outside the current user's assigned batch scope",ex.getMessage());
  verify(assignments,never()).gradebookAssignments(batch,learner);
 }
 @Test void staffCanReadLearnerWithinTheirAssignedBatchScope(){
  when(enrollment.activeBatchIdsForMembership(learner)).thenReturn(Set.of(batch));
  when(enrollment.activeBatchIdsForMembership(viewer)).thenReturn(Set.of(batch));
  assertEquals(learner,service.learner(batch,learner).membershipId());
  verify(assignments).gradebookAssignments(batch,learner);
 }
 @Test void academicAdministratorCanReadLearnerAcrossBatches(){
  when(enrollment.activeBatchIdsForMembership(learner)).thenReturn(Set.of(batch));
  when(memberships.hasSystemRole(viewer,SystemRoleKey.ACADEMIC_ADMINISTRATOR)).thenReturn(true);
  assertEquals(learner,service.learner(batch,learner).membershipId());
  verify(assignments).gradebookAssignments(batch,learner);
 }
 @Test void branchScopedReaderCanReadWithinTheirAssignedBranch(){
  when(enrollment.activeBatchIdsForMembership(learner)).thenReturn(Set.of(batch));
  when(auth.currentScope(PermissionKey.GRADEBOOK_VIEW)).thenReturn(new AuthorizationService.AccessScope(false,Set.of(branch)));
  assertEquals(learner,service.learner(batch,learner).membershipId());
  verify(assignments).gradebookAssignments(batch,learner);
 }
}
