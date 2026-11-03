package com.universalplatform.tenancy;

import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the one local demonstration tenant used when the native launcher runs
 * without an external identity provider. This is never active outside the
 * explicit local-native Spring profile.
 */
@Service
@Profile("local-native")
@ConditionalOnProperty(prefix = "platform.local-auth", name = "enabled", havingValue = "true")
public class LocalTenantBootstrapService {
    public static final String LOCAL_TENANT_SLUG = "uep-local";

    private final TenantRepository tenants;
    private volatile UUID tenantId;

    public LocalTenantBootstrapService(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Transactional
    public UUID ensureLocalTenant(UUID preferredId) {
        Tenant tenant = tenants.findBySlug(LOCAL_TENANT_SLUG).orElse(null);
        if (tenant == null && preferredId != null) {
            Tenant conflicting = tenants.findById(preferredId).orElse(null);
            if (conflicting != null) {
                throw new IllegalStateException(
                        "The configured local tenant ID belongs to a different tenant. Preserve the local state and database pairing.");
            }
        }
        if (tenant == null) {
            Instant now = Instant.now();
            UUID id = preferredId == null ? UUID.randomUUID() : preferredId;
            tenant = tenants.save(new Tenant(id, LOCAL_TENANT_SLUG, "UEP Local Development Institution", now));
        }
        if (tenant.status() != TenantStatus.ACTIVE) {
            throw new IllegalStateException("The existing UEP local tenant is not active; refusing to reactivate it automatically.");
        }
        tenant.setDeploymentPlatform("local");
        tenant.setDeploymentStatus("active");
        tenant = tenants.saveAndFlush(tenant);
        tenantId = tenant.id();
        return tenantId;
    }

    public UUID requireTenantId() {
        UUID current = tenantId;
        if (current == null) {
            throw new IllegalStateException("The local UEP tenant has not finished bootstrapping.");
        }
        return current;
    }
}
