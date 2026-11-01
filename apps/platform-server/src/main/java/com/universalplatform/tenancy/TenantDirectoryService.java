package com.universalplatform.tenancy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class TenantDirectoryService implements TenantDirectory {
    private final TenantRepository tenants;

    TenantDirectoryService(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Override
    @Transactional
    public Optional<TenantSummary> find(UUID tenantId) {
        return tenants.findById(tenantId)
                .map(t -> new TenantSummary(
                        t.id(),
                        t.slug(),
                        t.displayName(),
                        t.status(),
                        t.deploymentPlatform(),
                        t.deploymentStatus(),
                        t.licensedUserCount(),
                        t.maxLicensedUsers(),
                        t.subscriptionTier(),
                        t.customDomain()
                ));
    }

    @Transactional
    public List<Tenant> findAll() {
        return tenants.findAll();
    }

    @Transactional
    public Tenant details(UUID id) {
        return tenants.findById(id)
                .orElseThrow(() -> new TenantNotFoundException(id));
    }

    @Transactional
    public List<Tenant> findByDeploymentStatus(String platform, String status) {
        return tenants.findByDeploymentStatus(platform, status);
    }

    @Transactional
    public List<Tenant> findAtLicenseLimit() {
        return tenants.findAtLicenseLimit();
    }

    @Transactional
    public Tenant create(String slug, String displayName, String subscriptionTier) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Tenant tenant = new Tenant(id, slug, displayName, now);
        tenant.setSubscriptionTier(subscriptionTier);
        return tenants.save(tenant);
    }

    @Transactional
    public Tenant update(UUID id, String displayName, String subscriptionTier) {
        Tenant tenant = details(id);
        if (displayName != null) {
            tenant.setDisplayName(displayName);
        }
        if (subscriptionTier != null) {
            tenant.setSubscriptionTier(subscriptionTier);
        }
        return tenants.save(tenant);
    }

    @Transactional
    public Tenant updateDeployment(UUID id, String platform, String status) {
        Tenant tenant = details(id);
        tenant.setDeploymentPlatform(platform);
        tenant.setDeploymentStatus(status);
        tenant.setLastActivityAt(Instant.now());
        return tenants.save(tenant);
    }

    @Transactional
    public Tenant incrementLicensedUsers(UUID id) {
        Tenant tenant = details(id);
        tenant.setLicensedUserCount(tenant.licensedUserCount() + 1);
        tenant.setLastActivityAt(Instant.now());
        return tenants.save(tenant);
    }

    @Transactional
    public Tenant decrementLicensedUsers(UUID id) {
        Tenant tenant = details(id);
        if (tenant.licensedUserCount() > 0) {
            tenant.setLicensedUserCount(tenant.licensedUserCount() - 1);
        }
        tenant.setLastActivityAt(Instant.now());
        return tenants.save(tenant);
    }

    @Transactional
    public void updateLicensedUserCount(UUID id, int count, Instant now) {
        Tenant tenant = details(id);
        tenant.setLicensedUserCount(count);
        tenant.setLastActivityAt(now);
        tenants.save(tenant);
    }

    @Transactional
    public void updateDeploymentStatus(UUID id, String status, Instant now) {
        Tenant tenant = details(id);
        tenant.setDeploymentStatus(status);
        tenant.setLastActivityAt(now);
        tenants.save(tenant);
    }

    // Helper methods for stats
    public TenantStats stats(UUID id) {
        Tenant tenant = details(id);
        // In a real implementation, these would come from actual counts
        // of teachers, students, sessions, etc. in the tenant's data
        return new TenantStats(
                0, // totalTeachers - would query user/teacher count
                0, // totalStudents - would query student count
                0, // activeSessions - would query live class sessions
                0, // totalAssessments - would query assessment count
                0, // totalAnnouncements - would query announcement count
                0.0 // utilizationPercentage
        );
    }
}
