package com.universalplatform.tenancy;

import java.util.Optional;
import java.util.UUID;

public interface TenantDirectory {
    Optional<TenantSummary> find(UUID tenantId);

    record TenantSummary(UUID id, String slug, String displayName, TenantStatus status) {}
}
