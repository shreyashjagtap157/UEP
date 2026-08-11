package com.universalplatform.licensing;

import java.util.Arrays;
import java.util.Objects;

public record SignedLicenseEnvelope(String keyId, byte[] payload, byte[] signature) {
    public SignedLicenseEnvelope {
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(signature, "signature");
        if (keyId.isBlank()) throw new IllegalArgumentException("keyId must not be blank");
        payload = Arrays.copyOf(payload, payload.length);
        signature = Arrays.copyOf(signature, signature.length);
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }

    @Override
    public byte[] signature() {
        return Arrays.copyOf(signature, signature.length);
    }
}
