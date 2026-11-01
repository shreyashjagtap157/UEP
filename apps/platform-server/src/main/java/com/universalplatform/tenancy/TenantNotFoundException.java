package com.universalplatform.tenancy;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
class TenantNotFoundException extends RuntimeException {
    TenantNotFoundException(UUID id) {
        super("Tenant not found with ID: " + id);
    }
}