package com.universalplatform.assessment;

import jakarta.persistence.Column; import jakarta.persistence.Entity; import jakarta.persistence.Id; import jakarta.persistence.Table; import jakarta.persistence.Version;
import java.time.Instant; import java.util.UUID;

@Entity @Table(name="answer")
class Answer { @Id private UUID id; @Column(nullable=false) private UUID tenantId; @Column(nullable=false) private UUID attemptId; @Column(nullable=false) private UUID assessmentQuestionId; @Column(nullable=false,columnDefinition="text") private String payloadJson; @Column(nullable=false) private long serverSequence; @Column(nullable=false) private Instant savedAt; @Column(nullable=false,length=120) private String idempotencyKey; @Version private long version;
 protected Answer(){} Answer(UUID id,UUID tenantId,UUID attemptId,UUID assessmentQuestionId,String payloadJson,long serverSequence,String idempotencyKey,Instant now){this.id=id;this.tenantId=tenantId;this.attemptId=attemptId;this.assessmentQuestionId=assessmentQuestionId;this.payloadJson=payloadJson;this.serverSequence=serverSequence;this.idempotencyKey=idempotencyKey;this.savedAt=now;}
 UUID id(){return id;} UUID attemptId(){return attemptId;} UUID assessmentQuestionId(){return assessmentQuestionId;} String payloadJson(){return payloadJson;} long serverSequence(){return serverSequence;} Instant savedAt(){return savedAt;} String idempotencyKey(){return idempotencyKey;} long version(){return version;}
 void update(String payloadJson,long sequence,Instant now){this.payloadJson=payloadJson;this.serverSequence=sequence;this.savedAt=now;}
}
