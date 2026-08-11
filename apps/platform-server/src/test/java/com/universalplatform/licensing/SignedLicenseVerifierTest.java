package com.universalplatform.licensing;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SignedLicenseVerifierTest {
    @Test
    void acceptsAuthenticEd25519EnvelopeAndRejectsTampering() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] payload = "tenant-license-revision-1".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(payload);
        byte[] signature = signer.sign();

        SignedLicenseVerifier verifier = new SignedLicenseVerifier(keyId ->
                "vendor-2026".equals(keyId) ? Optional.of(pair.getPublic()) : Optional.empty());

        assertThatCode(() -> verifier.verify(new SignedLicenseEnvelope("vendor-2026", payload, signature)))
                .doesNotThrowAnyException();

        byte[] tampered = "tenant-license-revision-2".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> verifier.verify(new SignedLicenseEnvelope("vendor-2026", tampered, signature)))
                .isInstanceOf(LicenseVerificationException.class);
    }
}
