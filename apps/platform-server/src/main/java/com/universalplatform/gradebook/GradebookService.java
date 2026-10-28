package com.universalplatform.gradebook;
import com.universalplatform.assignment.*; import com.universalplatform.enrollment.EnrollmentDirectory; import com.universalplatform.identity.*; import com.universalplatform.security.TenantContext; import java.math.*; import java.util.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
@Service class GradebookService {
 private final TenantContext tenant; private final AuthorizationService auth; private final MembershipDirectory memberships; private final EnrollmentDirectory enrollment; private final AssignmentDirectory assignments;
 GradebookService(TenantContext t,AuthorizationService a,MembershipDirectory m,EnrollmentDirectory e,AssignmentDirectory assignments){tenant=t;auth=a;memberships=m;enrollment=e;this.assignments=assignments;}
 @Transactional(readOnly=true)
 GradebookView myBatch(UUID batchId){
  UUID viewerMembershipId=memberships.current().membershipId();
  var batch=enrollment.requireBatch(batchId);
  auth.require(PermissionKey.GRADEBOOK_VIEW,batch.branchId());
  if(!enrollment.activeBatchIdsForMembership(viewerMembershipId).contains(batchId))throw new SecurityException("Batch is not visible to current user");
  return build(batchId,viewerMembershipId);
 }
 @Transactional(readOnly=true)
 GradebookView learner(UUID batchId,UUID learnerMembershipId){
  UUID viewerMembershipId=memberships.current().membershipId();
  var batch=enrollment.requireBatch(batchId);
  auth.require(PermissionKey.GRADEBOOK_VIEW,batch.branchId());
  if(!enrollment.activeBatchIdsForMembership(learnerMembershipId).contains(batchId))throw new SecurityException("Learner is not enrolled in batch");
  if(!learnerMembershipId.equals(viewerMembershipId))requireOtherLearnerScope(viewerMembershipId,batch);
  return build(batchId,learnerMembershipId);
 }
 private void requireOtherLearnerScope(UUID viewerMembershipId,com.universalplatform.enrollment.EnrollmentDirectory.BatchReference batch){
  if(memberships.hasSystemRole(viewerMembershipId,SystemRoleKey.STUDENT))throw new SecurityException("Students may only view their own gradebook");
  if(memberships.hasSystemRole(viewerMembershipId,SystemRoleKey.GUARDIAN))throw new SecurityException("Guardian gradebook access requires a verified learner relationship");
  if(hasAnyRole(viewerMembershipId,SystemRoleKey.ORGANIZATION_OWNER,SystemRoleKey.ORGANIZATION_ADMINISTRATOR,SystemRoleKey.ACADEMIC_ADMINISTRATOR,SystemRoleKey.EXAM_CONTROLLER))return;
  var scope=auth.currentScope(PermissionKey.GRADEBOOK_VIEW);
  if(batch.branchId()!=null&&scope.branchIds().contains(batch.branchId()))return;
  if(!enrollment.activeBatchIdsForMembership(viewerMembershipId).contains(batch.id()))throw new SecurityException("Learner is outside the current user's assigned batch scope");
 }
 private boolean hasAnyRole(UUID membershipId,SystemRoleKey... roles){
  for(SystemRoleKey role:roles)if(memberships.hasSystemRole(membershipId,role))return true;
  return false;
 }
 private GradebookView build(UUID batchId,UUID membershipId){List<AssignmentDirectory.GradebookAssignment> raw=assignments.gradebookAssignments(batchId,membershipId);List<GradebookEntry> entries=raw.stream().map(x->new GradebookEntry(x.assignmentId(),x.title(),x.maxPoints(),x.weightBasisPoints(),x.awardedPoints(),x.status())).toList();List<GradebookFormula.GradeEntry> calc=raw.stream().map(x->new GradebookFormula.GradeEntry(x.maxPoints(),x.weightBasisPoints(),x.awardedPoints())).toList();var f=GradebookFormula.compute(calc);return new GradebookView(batchId,membershipId,f.weightedPoints(),f.completedAssignments(),f.totalAssignments(),entries);}
 public record GradebookView(UUID batchId,UUID membershipId,BigDecimal weightedPoints,int completedAssignments,int totalAssignments,List<GradebookEntry> entries){}
 public record GradebookEntry(UUID assignmentId,String title,int maxPoints,int weightBasisPoints,Integer awardedPoints,AssignmentStatus status){}
}
