package com.universalplatform.organization;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.universalplatform.organization")
class OrganizationApiExceptionHandler {
    @ExceptionHandler(OrganizationNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(OrganizationNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "ORGANIZATION_RESOURCE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler({OrganizationConflictException.class, ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<Map<String, String>> conflict(RuntimeException exception) {
        String message = exception instanceof OrganizationConflictException
                ? exception.getMessage()
                : "Request conflicts with the current organization state";
        return response(HttpStatus.CONFLICT, "ORGANIZATION_CONFLICT", message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_ORGANIZATION_REQUEST", exception.getMessage());
    }

    private static ResponseEntity<Map<String, String>> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }
}
