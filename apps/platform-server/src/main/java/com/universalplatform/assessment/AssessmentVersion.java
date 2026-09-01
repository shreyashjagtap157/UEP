package com.universalplatform.assessment;

import jakarta.persistence.Column; import jakarta.persistence.Entity; import jakarta.persistence.Id; import jakarta.persistence.Table;
import java.time.Instant; import java.util.UUID;

@Entity @Table(name="assessment_version")
class AssessmentVersion {
 @Id private UUID id; @Column(nullable=false) private UUID tenantId; @Column(nullable=false) private UUID assessmentId; @Column(nullable=false) private int versionNumber;
 @Column(nullable=false) private Integer durationSeconds; @Column(nullable=false) private Integer maxAttempts; @Column(nullable=false) private Integer totalMarks;
 @Column(nullable=false) private Integer passMarks; @Column(nullable=false) private boolean shuffleQuestions; @Column(nullable=false) private boolean allowBacktracking;
 @Column(nullable=false) private Instant availableFrom; @Column(nullable=false) private Instant availableUntil; @Column(nullable=false,updatable=false) private Instant createdAt;
 @Column(nullable=false,columnDefinition="text") private String settingsJson;
 protected AssessmentVersion() {}
 AssessmentVersion(UUID id,UUID tenantId,UUID assessmentId,int versionNumber,Integer durationSeconds,Integer maxAttempts,Integer totalMarks,Integer passMarks,boolean shuffleQuestions,boolean allowBacktracking,Instant availableFrom,Instant availableUntil,String settingsJson,Instant now){
  this.id=id;this.tenantId=tenantId;this.assessmentId=assessmentId;this.versionNumber=versionNumber;this.durationSeconds=durationSeconds;this.maxAttempts=maxAttempts;this.totalMarks=totalMarks;this.passMarks=passMarks;this.shuffleQuestions=shuffleQuestions;this.allowBacktracking=allowBacktracking;this.availableFrom=availableFrom;this.availableUntil=availableUntil;this.settingsJson=settingsJson;this.createdAt=now;
 }
 UUID id(){return id;} UUID tenantId(){return tenantId;} UUID assessmentId(){return assessmentId;} int versionNumber(){return versionNumber;} Integer durationSeconds(){return durationSeconds;} Integer maxAttempts(){return maxAttempts;} Integer totalMarks(){return totalMarks;} Integer passMarks(){return passMarks;} boolean shuffleQuestions(){return shuffleQuestions;} boolean allowBacktracking(){return allowBacktracking;} Instant availableFrom(){return availableFrom;} Instant availableUntil(){return availableUntil;} String settingsJson(){return settingsJson;}
}
