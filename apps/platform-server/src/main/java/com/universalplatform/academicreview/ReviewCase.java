package com.universalplatform.academicreview;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="review_case") class ReviewCase {
 @Id private UUID id; @Column(nullable=false) private UUID tenantId; @Column(nullable=false) private UUID attemptId; @Column(nullable=false) private UUID membershipId; @Column(nullable=true) private UUID targetAnswerId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private ReviewType type; @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ReviewStatus status;
 @Column(nullable=false,columnDefinition="text") private String subject; @Column(nullable=false,columnDefinition="text") private String openingArgument;
 @Column(nullable=false) private int version; @Column(nullable=false) private Instant createdAt; @Column(nullable=false) private Instant updatedAt;
 protected ReviewCase(){}
 ReviewCase(UUID id,UUID tenantId,UUID attemptId,UUID membershipId,UUID targetAnswerId,ReviewType type,String subject,String openingArgument,Instant now){this.id=id;this.tenantId=tenantId;this.attemptId=attemptId;this.membershipId=membershipId;this.targetAnswerId=targetAnswerId;this.type=type;this.subject=subject;this.openingArgument=openingArgument;this.status=ReviewStatus.OPEN;this.version=0;this.createdAt=now;this.updatedAt=now;}
 UUID id(){return id;} UUID tenantId(){return tenantId;} UUID attemptId(){return attemptId;} UUID membershipId(){return membershipId;} UUID targetAnswerId(){return targetAnswerId;} ReviewType type(){return type;} ReviewStatus status(){return status;} String subject(){return subject;} String openingArgument(){return openingArgument;} int version(){return version;} Instant createdAt(){return createdAt;} Instant updatedAt(){return updatedAt;}
 void status(ReviewStatus s,Instant now){status=s;updatedAt=now;version++;}
}
