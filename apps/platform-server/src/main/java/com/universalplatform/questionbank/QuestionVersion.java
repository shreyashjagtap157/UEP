package com.universalplatform.questionbank;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "question_version")
class QuestionVersion {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID questionId;
    @Column(nullable = false) private int versionNumber;
    @Column(nullable = false, length = 32) @Enumerated(EnumType.STRING) private QuestionType type;
    @Column(nullable = false, length = 32) private String difficulty;
    @Column(length = 64) private String language;
    @Column(nullable = false, columnDefinition = "text") private String payloadJson;
    @Column(nullable = false) private int positiveMarks;
    @Column(nullable = false) private int negativeMarks;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    protected QuestionVersion() {}
    QuestionVersion(UUID id, UUID tenantId, UUID questionId, int versionNumber, QuestionType type,
                    String difficulty, String language, String payloadJson, int positiveMarks, int negativeMarks, Instant now) {
        this.id = id; this.tenantId = tenantId; this.questionId = questionId; this.versionNumber = versionNumber;
        this.type = type; this.difficulty = difficulty; this.language = language; this.payloadJson = payloadJson;
        this.positiveMarks = positiveMarks; this.negativeMarks = negativeMarks; this.createdAt = now;
    }
    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    UUID questionId() { return questionId; }
    int versionNumber() { return versionNumber; }
    QuestionType type() { return type; }
    String difficulty() { return difficulty; }
    String language() { return language; }
    String payloadJson() { return payloadJson; }
    int positiveMarks() { return positiveMarks; }
    int negativeMarks() { return negativeMarks; }
    Instant createdAt() { return createdAt; }
}
