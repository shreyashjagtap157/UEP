package com.universalplatform.credential;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.universalplatform.credential")
class CredentialApiExceptionHandler {
    @ExceptionHandler(CredentialNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", "CREDENTIAL_NOT_FOUND", "message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("code", "INVALID_CREDENTIAL_REQUEST", "message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> conflict(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "CREDENTIAL_CONFLICT", "message", exception.getMessage()));
    }
}
