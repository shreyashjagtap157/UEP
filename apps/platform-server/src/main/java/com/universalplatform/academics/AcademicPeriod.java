package com.universalplatform.academics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "academic_period")
class AcademicPeriod {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false, length = 64) private String code;
    @Column(nullable = false, length = 200) private String displayName;
    @Column(nullable = false) private LocalDate startsOn;
    @Column(nullable = false) private LocalDate endsOn;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private AcademicPeriodStatus status;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected AcademicPeriod() {}

    AcademicPeriod(UUID id, UUID tenantId, String code, String displayName, LocalDate startsOn, LocalDate endsOn, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.displayName = displayName;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.status = AcademicPeriodStatus.PLANNED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID id() { return id; }
    UUID tenantId() { return tenantId; }
    String code() { return code; }
    String displayName() { return displayName; }
    LocalDate startsOn() { return startsOn; }
    LocalDate endsOn() { return endsOn; }
    AcademicPeriodStatus status() { return status; }
    Instant createdAt() { return createdAt; }
    long version() { return version; }

    void update(String code, String displayName, LocalDate startsOn, LocalDate endsOn, AcademicPeriodStatus status, Instant now) {
        this.code = code;
        this.displayName = displayName;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.status = status;
        this.updatedAt = now;
    }
}
