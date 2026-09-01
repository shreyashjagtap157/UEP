package com.universalplatform.assignment;
import java.util.*;
public interface AssignmentDirectory {
 AssignmentReference requireAssignment(UUID assignmentId);
 GradebookAssignment gradebookAssignment(UUID assignmentId, UUID batchId, UUID membershipId);
 List<GradebookAssignment> gradebookAssignments(UUID batchId, UUID membershipId);
 record AssignmentReference(UUID id,String title,int maxPoints,boolean published){}
 record GradebookAssignment(UUID assignmentId,String title,int maxPoints,int weightBasisPoints,Integer awardedPoints,AssignmentStatus status){}
}
