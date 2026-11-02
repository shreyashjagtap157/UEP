package com.universalplatform.storage;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A storage capacity or provider-policy rejection with an actionable HTTP response. */
public final class StorageQuotaException extends ResponseStatusException {
    private static final long serialVersionUID = 1L;

    private StorageQuotaException(HttpStatus status, String reason) {
        super(status, reason);
    }

    static StorageQuotaException providerUnavailable(String provider) {
        return new StorageQuotaException(HttpStatus.SERVICE_UNAVAILABLE,
                "Storage provider " + provider + " is disabled by the deployment storage policy");
    }

    static StorageQuotaException capacityExceeded(String scope) {
        return new StorageQuotaException(HttpStatus.INSUFFICIENT_STORAGE,
                "Storage quota exceeded for " + scope);
    }

    static StorageQuotaException invalidReservation(String detail) {
        return new StorageQuotaException(HttpStatus.CONFLICT, "Storage reservation conflict: " + detail);
    }
}
