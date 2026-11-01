package com.universalplatform.storage;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the server-owned storage destination configured for a tenant.
 * Implementations belong to the module that owns tenant integration bindings.
 */
public interface TenantStorageBindingResolver {
    Optional<TenantStorageBinding> findByTenantId(UUID tenantId);
}
