package com.universalplatform.licensing;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;

public final class SignedLicenseVerifier {
    private final LicenseVerificationKeyResolver keys;

    public SignedLicenseVerifier(LicenseVerificationKeyResolver keys) {
        this.keys = keys;
    }

    public void verify(SignedLicenseEnvelope envelope) {
        PublicKey key = keys.resolve(envelope.keyId())
                .orElseThrow(() -> new LicenseVerificationException("Unknown license verification key: " + envelope.keyId()));
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(envelope.payload());
            if (!verifier.verify(envelope.signature())) {
                throw new LicenseVerificationException("License signature verification failed");
            }
        } catch (GeneralSecurityException exception) {
            throw new LicenseVerificationException("Unable to verify signed license", exception);
        }
    }
}
