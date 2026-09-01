package com.universalplatform.questionbank;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "question")
class Question {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 20) @Enumerated(EnumType.STRING) private QuestionStatus status;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected Question() {}
    Question(UUID id, UUID tenantId, String title, Instant now) {
        this.id = id; this.tenantId = tenantId; this.title = title;
        this.status = QuestionStatus.DRAFT; this.createdAt = now; this.updatedAt = now;
    }
    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    String title() { return title; }
    QuestionStatus status() { return status; }
    long version() { return version; }
    void update(String title, Instant now) { this.title = title; this.updatedAt = now; }
    void changeStatus(QuestionStatus status, Instant now) { this.status = status; this.updatedAt = now; }
}
