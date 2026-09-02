package com.universalplatform.licensing;

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
@Table(name = "subscription")
public class Subscription {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SubscriptionStatus status;

    @Column(nullable = false)
    private Instant startsAt;

    private Instant graceEndsAt;
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LicenseAuthorityKind authorityKind;

    @Column(length = 120)
    private String externalLicenseId;

    @Column(nullable = false)
    private long licenseRevision;

    @Column(nullable = false, length = 64)
    private String planCode = "TRIAL";

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(nullable = false, precision = 19, scale = 2)
    private java.math.BigDecimal recurringAmount = java.math.BigDecimal.ZERO;

    private boolean autoRenew = false;
    private Instant trialEndsAt;

    @Version
    private long version;

    protected Subscription() {}

    Subscription(UUID id, UUID tenantId, SubscriptionStatus status, Instant startsAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.status = status;
        this.startsAt = startsAt;
        this.authorityKind = LicenseAuthorityKind.PLATFORM_MANAGED;
        this.licenseRevision = 0;
    }

    public UUID tenantId() { return tenantId; }
    public SubscriptionStatus status() { return status; }
    public Instant graceEndsAt() { return graceEndsAt; }
    public Instant expiresAt() { return expiresAt; }
    public LicenseAuthorityKind authorityKind() { return authorityKind; }
    public String externalLicenseId() { return externalLicenseId; }
    public long licenseRevision() { return licenseRevision; }
    public String planCode() { return planCode; }
    public String currency() { return currency; }
    public java.math.BigDecimal recurringAmount() { return recurringAmount; }
    public boolean autoRenew() { return autoRenew; }
    public Instant trialEndsAt() { return trialEndsAt; }
    public void configurePlan(String plan, java.math.BigDecimal amount, String currencyCode, boolean renew) {
        java.util.Objects.requireNonNull(plan, "planCode");
        if (plan.isBlank()) throw new IllegalArgumentException("planCode must not be blank");
        if (amount == null || amount.signum() < 0 || amount.scale() > 2) throw new IllegalArgumentException("Invalid recurring amount");
        if (currencyCode == null || !currencyCode.matches("[A-Za-z]{3}")) throw new IllegalArgumentException("Invalid currency");
        planCode = plan.trim().toUpperCase(java.util.Locale.ROOT);
        recurringAmount = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        currency = currencyCode.toUpperCase(java.util.Locale.ROOT);
        autoRenew = renew;
        licenseRevision = Math.addExact(licenseRevision, 1);
    }
    public void configureTrial(Instant trialEnds) {
        trialEndsAt = trialEnds;
        if (trialEnds != null) status = SubscriptionStatus.TRIAL;
    }
    public void activate(Instant expires) { status = SubscriptionStatus.ACTIVE; expiresAt = expires; }
    public void enterGrace(Instant ends) { if (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.EXPIRED) { status = SubscriptionStatus.GRACE; graceEndsAt = ends; } }
    public void suspend() { status = SubscriptionStatus.SUSPENDED; }
}
