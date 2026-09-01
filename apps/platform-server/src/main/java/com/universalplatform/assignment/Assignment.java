package com.universalplatform.assignment;
import jakarta.persistence.*; import java.time.*; import java.util.UUID;
@Entity @Table(name="assignment") class Assignment {
 @Id UUID id; @Column(nullable=false) UUID tenantId; @Column(nullable=false,length=240) String title; @Column(columnDefinition="text") String instructions;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) AssignmentStatus status; @Column(nullable=false) Integer maxPoints; @Column(nullable=false) Integer weightBasisPoints;
 @Column(nullable=false) Instant dueAt; @Column(nullable=false) Instant createdAt; @Column(nullable=false) Instant updatedAt; @Version @Column(nullable=false) long version;
 protected Assignment(){}
 Assignment(UUID id,UUID tenantId,String title,String instructions,AssignmentStatus status,int maxPoints,int weightBasisPoints,Instant dueAt,Instant now){this.id=id;this.tenantId=tenantId;this.title=title;this.instructions=instructions;this.status=status;this.maxPoints=maxPoints;this.weightBasisPoints=weightBasisPoints;this.dueAt=dueAt;this.createdAt=now;this.updatedAt=now;}
 UUID id(){return id;} UUID tenantId(){return tenantId;} String title(){return title;} String instructions(){return instructions;} AssignmentStatus status(){return status;} int maxPoints(){return maxPoints;} int weightBasisPoints(){return weightBasisPoints;} Instant dueAt(){return dueAt;} Instant createdAt(){return createdAt;} Instant updatedAt(){return updatedAt;} long version(){return version;}
 void update(String title,String instructions,int maxPoints,int weightBasisPoints,Instant dueAt,AssignmentStatus status,Instant now){if(this.status!=AssignmentStatus.DRAFT&&status==AssignmentStatus.DRAFT)throw new IllegalStateException("Published assignments cannot return to draft");this.title=title;this.instructions=instructions;this.maxPoints=maxPoints;this.weightBasisPoints=weightBasisPoints;this.dueAt=dueAt;this.status=status;this.updatedAt=now;}
}
