package com.universalplatform.integration;

import com.universalplatform.identity.IdentityConflictException;
import com.universalplatform.identity.IdentityNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.universalplatform.integration")
class IntegrationApiExceptionHandler {
    @ExceptionHandler(IdentityNotFoundException.class)
    ResponseEntity<Map<String, String>> identityNotFound(IdentityNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "IDENTITY_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(IdentityConflictException.class)
    ResponseEntity<Map<String, String>> identityConflict(IdentityConflictException exception) {
        return response(HttpStatus.CONFLICT, "IDENTITY_CONFLICT", exception.getMessage());
    }

    private static ResponseEntity<Map<String, String>> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }
}
