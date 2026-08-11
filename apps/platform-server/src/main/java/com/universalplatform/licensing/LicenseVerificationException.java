package com.universalplatform.licensing;

public final class LicenseVerificationException extends RuntimeException {
    public LicenseVerificationException(String message) {
        super(message);
    }

    public LicenseVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
