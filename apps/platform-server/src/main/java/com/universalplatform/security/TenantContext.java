package com.universalplatform.security;

import java.util.Optional;
import java.util.UUID;

public interface TenantContext {
    Optional<UUID> currentTenantId();

    default UUID requireTenantId() {
        return currentTenantId().orElseThrow(() -> new MissingTenantContextException("Authenticated tenant context is required"));
    }
}
