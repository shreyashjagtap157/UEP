package com.universalplatform.tenancy;

/**
 * Consumer-owned port for protecting cross-tenant control-plane operations.
 * The identity module supplies the implementation using the authenticated OIDC realm roles.
 */
public interface PlatformAdministratorGuard {
    void requirePlatformSuperAdmin();
}
