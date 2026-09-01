package com.universalplatform.assignment;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*; import java.util.*; import org.junit.jupiter.api.Test;
class AssignmentDomainInvariantTest {
 @Test void publishedAssignmentsCannotReturnToDraft(){var a=new Assignment(UUID.randomUUID(),UUID.randomUUID(),"A",null,AssignmentStatus.PUBLISHED,100,1000,Instant.now().plusSeconds(3600),Instant.now());assertThrows(IllegalStateException.class,()->a.update("A",null,100,1000,Instant.now().plusSeconds(3600),AssignmentStatus.DRAFT,Instant.now()));}
 @Test void submittedWorkCannotBeEdited(){var s=new AssignmentSubmission(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,SubmissionStatus.DRAFT,Instant.now(),"answer",null);s.submit(Instant.now());assertThrows(IllegalStateException.class,()->s.saveDraft("changed",null,Instant.now()));}
}
