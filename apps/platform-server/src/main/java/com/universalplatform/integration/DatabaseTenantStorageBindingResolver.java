package com.universalplatform.integration;

import com.universalplatform.storage.StorageProviderType;
import com.universalplatform.storage.TenantStorageBinding;
import com.universalplatform.storage.TenantStorageBindingResolver;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolves configured provider bindings from the integration module's authoritative tenant table. */
@Component
class DatabaseTenantStorageBindingResolver implements TenantStorageBindingResolver {
    private final ExternalStorageBindingRepository bindings;

    DatabaseTenantStorageBindingResolver(ExternalStorageBindingRepository bindings) {
        this.bindings = bindings;
    }

    @Override
    public Optional<TenantStorageBinding> findByTenantId(UUID tenantId) {
        return bindings.findByTenantId(tenantId)
                .map(binding -> new TenantStorageBinding(
                        StorageProviderType.valueOf(binding.providerType()),
                        binding.objectPrefix()));
    }
}
