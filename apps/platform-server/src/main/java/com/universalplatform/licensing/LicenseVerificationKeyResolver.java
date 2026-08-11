package com.universalplatform.licensing;

import java.security.PublicKey;
import java.util.Optional;

@FunctionalInterface
public interface LicenseVerificationKeyResolver {
    Optional<PublicKey> resolve(String keyId);
}
