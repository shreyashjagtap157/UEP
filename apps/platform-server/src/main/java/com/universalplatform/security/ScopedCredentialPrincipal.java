package com.universalplatform.security;
import java.util.Set;
import java.util.UUID;
/** Authentication principal supplied by a tenant-scoped API credential. */
public interface ScopedCredentialPrincipal {
    UUID credentialId();
    UUID tenantId();
    UUID membershipId();
    Set<String> scopes();
}
