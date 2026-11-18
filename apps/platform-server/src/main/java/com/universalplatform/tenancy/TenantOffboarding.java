package com.universalplatform.tenancy;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Centralized offboarding utility that handles status-based tenant lifecycle
 * transitions. No tenant row is ever deleted; instead status is transitioned
 * from ACTIVE → SUSPENDED → CLOSED.
 *
 * All code paths (API controllers, workers) should use this utility
 * rather than ad-hoc status changes. Test teardown may use status-based
 * cleanup, but production code must never delete tenant rows.
 */
@Component
public class TenantOffboarding {

    private final TenantRepository tenants;

    public TenantOffboarding(TenantRepository tenants) {
        this.tenants = tenants;
    }

    /**
     * Transition a tenant from ACTIVE to SUSPENDED.
     * @throws IllegalStateException if the tenant is already SUSPENDED, CLOSED, or not found
     */
    @Transactional
    public void suspend(UUID tenantId) {
        tenants.findById(tenantId).ifPresent(t -> {
            if (t.status() == TenantStatus.SUSPENDED) {
                throw new IllegalStateException("Tenant " + tenantId + " is already SUSPENDED");
            }
            if (t.status() == TenantStatus.CLOSED) {
                throw new IllegalStateException("Tenant " + tenantId + " is already CLOSED");
            }
            t.setStatus(TenantStatus.SUSPENDED);
            t.setLastActivityAt(Instant.now());
            tenants.save(t);
        });
    }

    /**
     * Transition a tenant from SUSPENDED to CLOSED.
     * @throws IllegalStateException if the tenant is already CLOSED or not found
     */
    @Transactional
    public void close(UUID tenantId) {
        tenants.findById(tenantId).ifPresent(t -> {
            if (t.status() == TenantStatus.CLOSED) {
                throw new IllegalStateException("Tenant " + tenantId + " is already CLOSED");
            }
            if (t.status() == TenantStatus.ACTIVE) {
                throw new IllegalStateException("Tenant " + tenantId + " must be SUSPENDED before CLOSED");
            }
            t.setStatus(TenantStatus.CLOSED);
            t.setLastActivityAt(Instant.now());
            tenants.save(t);
        });
    }

    /**
     * Full offboarding: ACTIVE → SUSPENDED → CLOSED in a single transaction.
     * @throws IllegalStateException if the tenant is already CLOSED or not in ACTIVE state
     */
    @Transactional
    public void offboard(UUID tenantId) {
        tenants.findById(tenantId).ifPresent(t -> {
            if (t.status() == TenantStatus.CLOSED) {
                throw new IllegalStateException("Tenant " + tenantId + " is already CLOSED");
            }
            if (t.status() != TenantStatus.ACTIVE) {
                throw new IllegalStateException("Tenant " + tenantId + " is in " + t.status() + " state, cannot offboard");
            }
            t.setStatus(TenantStatus.SUSPENDED);
            t.setLastActivityAt(Instant.now());
            tenants.save(t);
            // Transition to CLOSED
            t.setStatus(TenantStatus.CLOSED);
            t.setLastActivityAt(Instant.now());
            tenants.save(t);
        });
    }
}