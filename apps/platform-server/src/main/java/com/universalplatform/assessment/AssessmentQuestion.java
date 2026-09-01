package com.universalplatform.assessment;

import jakarta.persistence.Column; import jakarta.persistence.Entity; import jakarta.persistence.Id; import jakarta.persistence.Table;
import java.util.UUID;

@Entity @Table(name="assessment_question")
class AssessmentQuestion {
 @Id private UUID id; @Column(nullable=false) private UUID tenantId; @Column(nullable=false) private UUID assessmentVersionId; @Column(nullable=false) private UUID questionVersionId; @Column(nullable=false) private int ordinal; @Column(nullable=false) private int marks;
 protected AssessmentQuestion() {} AssessmentQuestion(UUID id,UUID tenantId,UUID assessmentVersionId,UUID questionVersionId,int ordinal,int marks){this.id=id;this.tenantId=tenantId;this.assessmentVersionId=assessmentVersionId;this.questionVersionId=questionVersionId;this.ordinal=ordinal;this.marks=marks;}
 UUID id(){return id;} UUID tenantId(){return tenantId;} UUID assessmentVersionId(){return assessmentVersionId;} UUID questionVersionId(){return questionVersionId;} int ordinal(){return ordinal;} int marks(){return marks;}
}
