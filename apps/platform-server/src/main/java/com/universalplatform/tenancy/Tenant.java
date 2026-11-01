package com.universalplatform.tenancy;

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
@Table(name = "tenant")
class Tenant {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false, length = 200)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TenantStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deployment_platform", nullable = false, length = 32)
    private String deploymentPlatform; // fly.io, render, koyeb, supabase, etc.

    @Column(name = "deployment_status", nullable = false, length = 32)
    private String deploymentStatus; // pending, active, failed, stopped

    @Column(name = "licensed_user_count", nullable = false)
    private int licensedUserCount; // current count of teachers + students

    @Column(name = "max_licensed_users", nullable = false)
    private int maxLicensedUsers; // subscription limit

    @Column(name = "onboarding_date", nullable = false)
    private Instant onboardingDate;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    @Column(name = "subscription_tier", nullable = false, length = 32)
    private String subscriptionTier; // free, pro, enterprise

    @Column(name = "custom_domain")
    private String customDomain; // for production deployment

    protected Tenant() {}

    Tenant(UUID id, String slug, String displayName, Instant createdAt) {
        this.id = id;
        this.slug = slug;
        this.displayName = displayName;
        this.status = TenantStatus.ACTIVE;
        this.deploymentPlatform = "fly.io";
        this.deploymentStatus = "pending";
        this.licensedUserCount = 0;
        this.maxLicensedUsers = 100;
        this.onboardingDate = createdAt;
        this.lastActivityAt = createdAt;
        this.subscriptionTier = "free";
        this.customDomain = null;
        this.createdAt = createdAt;
    }

    UUID id() { return id; }
    String slug() { return slug; }
    String displayName() { return displayName; }
    TenantStatus status() { return status; }
    String deploymentPlatform() { return deploymentPlatform; }
    String deploymentStatus() { return deploymentStatus; }
    int licensedUserCount() { return licensedUserCount; }
    int maxLicensedUsers() { return maxLicensedUsers; }
    Instant onboardingDate() { return onboardingDate; }
    Instant lastActivityAt() { return lastActivityAt; }
    String subscriptionTier() { return subscriptionTier; }
    String customDomain() { return customDomain; }

    // Setters for service layer
    void setDisplayName(String displayName) { this.displayName = displayName; }
    void setSubscriptionTier(String subscriptionTier) { this.subscriptionTier = subscriptionTier; }
    void setDeploymentPlatform(String deploymentPlatform) { this.deploymentPlatform = deploymentPlatform; }
    void setDeploymentStatus(String deploymentStatus) { this.deploymentStatus = deploymentStatus; }
    void setLicensedUserCount(int licensedUserCount) { this.licensedUserCount = licensedUserCount; }
    void setLastActivityAt(Instant lastActivityAt) { this.lastActivityAt = lastActivityAt; }
    void setCustomDomain(String customDomain) { this.customDomain = customDomain; }
}
