package com.universalplatform.identity;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.universalplatform.identity")
class IdentityApiExceptionHandler {
    @ExceptionHandler(IdentityNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(IdentityNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "IDENTITY_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler({IdentityConflictException.class, ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<Map<String, String>> conflict(RuntimeException exception) {
        String message = exception instanceof IdentityConflictException
                ? exception.getMessage()
                : "Request conflicts with the current identity state";
        return response(HttpStatus.CONFLICT, "IDENTITY_CONFLICT", message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_IDENTITY_REQUEST", exception.getMessage());
    }

    private static ResponseEntity<Map<String, String>> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }
}
