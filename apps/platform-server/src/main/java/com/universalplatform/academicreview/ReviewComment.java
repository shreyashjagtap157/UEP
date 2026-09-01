package com.universalplatform.academicreview;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="review_comment") class ReviewComment { @Id private UUID id; @Column(nullable=false) private UUID tenantId; @Column(nullable=false) private UUID reviewCaseId; @Column(nullable=false) private String actorSubject; @Column(nullable=false,columnDefinition="text") private String body; @Column(nullable=false) private Instant createdAt;
 protected ReviewComment(){} ReviewComment(UUID id,UUID tenantId,UUID reviewCaseId,String actorSubject,String body,Instant createdAt){this.id=id;this.tenantId=tenantId;this.reviewCaseId=reviewCaseId;this.actorSubject=actorSubject;this.body=body;this.createdAt=createdAt;}
 UUID id(){return id;} UUID tenantId(){return tenantId;} UUID reviewCaseId(){return reviewCaseId;} String actorSubject(){return actorSubject;} String body(){return body;} Instant createdAt(){return createdAt;}
}
