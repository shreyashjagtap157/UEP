package com.universalplatform.academicreview;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="answer_revision") class AnswerRevision {
 @Id private UUID id; @Column(nullable=false) private UUID tenantId; @Column(nullable=false) private UUID reviewCaseId; @Column(nullable=false) private UUID answerId; @Column(nullable=false) private int revisionNumber;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private AnswerRevisionStatus status; @Column(nullable=false,columnDefinition="text") private String proposedPayloadJson;
 @Column(nullable=false) private String actorSubject; @Column(nullable=false) private Instant createdAt;
 protected AnswerRevision(){}
 AnswerRevision(UUID id,UUID tenantId,UUID reviewCaseId,UUID answerId,int revisionNumber,String payload,String actorSubject,Instant createdAt){this.id=id;this.tenantId=tenantId;this.reviewCaseId=reviewCaseId;this.answerId=answerId;this.revisionNumber=revisionNumber;this.proposedPayloadJson=payload;this.actorSubject=actorSubject;this.createdAt=createdAt;this.status=AnswerRevisionStatus.PROPOSED;}
 UUID id(){return id;} UUID tenantId(){return tenantId;} UUID reviewCaseId(){return reviewCaseId;} UUID answerId(){return answerId;} int revisionNumber(){return revisionNumber;} AnswerRevisionStatus status(){return status;} String proposedPayloadJson(){return proposedPayloadJson;} String actorSubject(){return actorSubject;} Instant createdAt(){return createdAt;}
 void status(AnswerRevisionStatus s){status=s;}
}
