package com.universalplatform.tenancy;

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
    public Optional<TenantSummary> find(UUID tenantId) {
        return tenants.findById(tenantId)
                .map(t -> new TenantSummary(t.id(), t.slug(), t.displayName(), t.status()));
    }
}
