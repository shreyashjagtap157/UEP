package com.universalplatform.assessment;

import jakarta.persistence.Column; import jakarta.persistence.Entity; import jakarta.persistence.Id; import jakarta.persistence.Table;
import java.time.Instant; import java.util.UUID;

@Entity @Table(name="assessment_batch")
class AssessmentBatch { @Id private UUID id; @Column(nullable=false) private UUID tenantId; @Column(nullable=false) private UUID assessmentVersionId; @Column(nullable=false) private UUID batchId; @Column(nullable=false) private Instant assignedAt; protected AssessmentBatch(){} AssessmentBatch(UUID id,UUID tenantId,UUID assessmentVersionId,UUID batchId,Instant assignedAt){this.id=id;this.tenantId=tenantId;this.assessmentVersionId=assessmentVersionId;this.batchId=batchId;this.assignedAt=assignedAt;} UUID id(){return id;} UUID tenantId(){return tenantId;} UUID assessmentVersionId(){return assessmentVersionId;} UUID batchId(){return batchId;}}
