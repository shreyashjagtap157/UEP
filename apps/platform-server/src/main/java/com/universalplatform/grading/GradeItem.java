package com.universalplatform.grading;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="grade_item")
class GradeItem {
    @Id private UUID id;
    @Column(nullable=false) private UUID tenantId;
    @Column(nullable=false) private UUID gradeRevisionId;
    @Column(nullable=false) private UUID answerId;
    @Column(nullable=false) private UUID assessmentQuestionId;
    @Column(nullable=false) private int maxMarks;
    @Column(nullable=false) private int negativeMarks;
    @Column(nullable=false) private int systemScore;
    @Column(nullable=false) private Integer teacherScore;
    @Column(nullable=false) private int finalScore;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false,columnDefinition="text") private String explanationJson;
    @Column(nullable=false,columnDefinition="text") private String rubricJson;

    protected GradeItem(){}
    GradeItem(UUID id, UUID tenantId, UUID gradeRevisionId, UUID answerId, UUID assessmentQuestionId,
              int maxMarks, int negativeMarks, int systemScore, Integer teacherScore, int finalScore, Instant createdAt,
              String explanationJson, String rubricJson){
        this.id=id;this.tenantId=tenantId;this.gradeRevisionId=gradeRevisionId;this.answerId=answerId;this.assessmentQuestionId=assessmentQuestionId;
        this.maxMarks=maxMarks;this.negativeMarks=negativeMarks;this.systemScore=systemScore;this.teacherScore=teacherScore;this.finalScore=finalScore;this.createdAt=createdAt;
        this.explanationJson=explanationJson;this.rubricJson=rubricJson;
    }
    UUID id(){return id;} UUID tenantId(){return tenantId;} UUID gradeRevisionId(){return gradeRevisionId;} UUID answerId(){return answerId;}
    UUID assessmentQuestionId(){return assessmentQuestionId;} int maxMarks(){return maxMarks;} int negativeMarks(){return negativeMarks;} int systemScore(){return systemScore;}
    Integer teacherScore(){return teacherScore;} int finalScore(){return finalScore;} Instant createdAt(){return createdAt;}
    String explanationJson(){return explanationJson;} String rubricJson(){return rubricJson;}
}
