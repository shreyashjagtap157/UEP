package com.universalplatform.grading;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "grade_revision")
class GradeRevision {
    @Id private UUID id;
    @Column(nullable=false) private UUID tenantId;
    @Column(nullable=false) private UUID attemptId;
    @Column(nullable=false) private int revisionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private GradeRevisionStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private GradeSource source;
    @Column(nullable=false) private int awardedMarks;
    @Column(nullable=false) private int maxMarks;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private String actorSubject;
    @Column(nullable=false,columnDefinition="text") private String rationaleJson;

    protected GradeRevision() {}
    GradeRevision(UUID id, UUID tenantId, UUID attemptId, int revisionNumber, GradeRevisionStatus status,
                  GradeSource source, int awardedMarks, int maxMarks, Instant createdAt,
                  String actorSubject, String rationaleJson) {
        this.id=id; this.tenantId=tenantId; this.attemptId=attemptId; this.revisionNumber=revisionNumber;
        this.status=status; this.source=source; this.awardedMarks=awardedMarks; this.maxMarks=maxMarks;
        this.createdAt=createdAt; this.actorSubject=actorSubject; this.rationaleJson=rationaleJson;
    }
    UUID id(){return id;} UUID tenantId(){return tenantId;} UUID attemptId(){return attemptId;}
    int revisionNumber(){return revisionNumber;} GradeRevisionStatus status(){return status;} GradeSource source(){return source;}
    int awardedMarks(){return awardedMarks;} int maxMarks(){return maxMarks;} Instant createdAt(){return createdAt;}
    String actorSubject(){return actorSubject;} String rationaleJson(){return rationaleJson;}
    void publish(){this.status=GradeRevisionStatus.PUBLISHED;}
    void supersede(){this.status=GradeRevisionStatus.SUPERSEDED;}
    void setStatus(GradeRevisionStatus status){this.status=status;}
    void setAwardedMarks(int awardedMarks){this.awardedMarks=awardedMarks;}
}
