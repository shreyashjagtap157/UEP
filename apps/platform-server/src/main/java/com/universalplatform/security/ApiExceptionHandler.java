package com.universalplatform.security;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
final class ApiExceptionHandler {
    @ExceptionHandler(MissingTenantContextException.class)
    ResponseEntity<Map<String, String>> missingTenant(MissingTenantContextException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("code", "TENANT_CONTEXT_REQUIRED", "message", exception.getMessage()));
    }
}
