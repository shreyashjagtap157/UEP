package com.universalplatform.tenancy;

import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the isolated local OIDC tenant used by the opt-in Keycloak developer profile.
 * Its ID is deliberately identical to the tenant_id user attribute in the local realm export.
 */
@Service
@Profile("local-keycloak")
public class LocalKeycloakTenantBootstrapService {
    public static final UUID TENANT_ID = UUID.fromString("b1b2789e-9b87-4c9d-a6f7-6f5d7e8a1d44");
    public static final String TENANT_SLUG = "uep-keycloak-local";
    public static final String TENANT_DISPLAY_NAME = "UEP Local Keycloak Institution";

    private final TenantRepository tenants;

    public LocalKeycloakTenantBootstrapService(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Transactional
    public UUID ensureTenant() {
        Tenant tenant = tenants.findBySlug(TENANT_SLUG).orElse(null);
        if (tenant == null) {
            Tenant conflictingId = tenants.findById(TENANT_ID).orElse(null);
            if (conflictingId != null) {
                throw new IllegalStateException(
                        "The reserved local Keycloak tenant ID belongs to another tenant; refusing to reuse it.");
            }
            tenant = tenants.save(new Tenant(TENANT_ID, TENANT_SLUG, TENANT_DISPLAY_NAME, Instant.now()));
        }
        if (!tenant.id().equals(TENANT_ID)) {
            throw new IllegalStateException(
                    "The local Keycloak tenant slug is already bound to a different ID; refusing to remap OIDC claims.");
        }
        if (tenant.status() != TenantStatus.ACTIVE) {
            throw new IllegalStateException(
                    "The existing local Keycloak tenant is not active; refusing to reactivate it automatically.");
        }
        tenant.setDeploymentPlatform("local");
        tenant.setDeploymentStatus("active");
        tenants.saveAndFlush(tenant);
        return tenant.id();
    }
}
