package com.universalplatform.content;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="learning_resource_version")
class LearningResourceVersion { @Id UUID id; @Column(nullable=false) UUID tenantId; @Column(nullable=false) UUID resourceId; @Column(nullable=false) int versionNumber; UUID storageObjectId; @Column(columnDefinition="text") String textualContent; @Column(length=1000) String changeNote; @Column(nullable=false) String createdBy; @Column(nullable=false) Instant createdAt;
 protected LearningResourceVersion(){} LearningResourceVersion(UUID id,UUID tenantId,UUID resourceId,int versionNumber,UUID storageObjectId,String text,String note,String actor,Instant now){this.id=id;this.tenantId=tenantId;this.resourceId=resourceId;this.versionNumber=versionNumber;this.storageObjectId=storageObjectId;this.textualContent=text;this.changeNote=note;this.createdBy=actor;this.createdAt=now;}
}
