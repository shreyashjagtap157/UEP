package com.universalplatform.security;

/**
 * Narrow authorization port used by API credential lifecycle operations.
 *
 * Keeping permission names out of this port avoids a compile-time dependency
 * from the security package back into the identity module.
 */
public interface ApiCredentialAuthorizer {
    void requireApiManage();
    void requireApiView();

    /** Validate that credential issuance cannot silently impersonate another tenant member. */
    void requireApiCredentialTarget(java.util.UUID membershipId);
}
