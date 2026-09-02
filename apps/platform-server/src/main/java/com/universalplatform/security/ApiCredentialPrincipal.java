package com.universalplatform.security;
import java.util.Set;
import java.util.UUID;
record ApiCredentialPrincipal(UUID credentialId, UUID tenantId, UUID membershipId, Set<String> scopes) implements ScopedCredentialPrincipal {}
