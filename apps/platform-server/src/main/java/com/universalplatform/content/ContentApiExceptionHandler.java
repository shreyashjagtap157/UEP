package com.universalplatform.content;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps content/upload domain failures to stable API error codes: validation failures to 400,
 * state conflicts (upload expired, SHA mismatch, downloads disabled, optimistic conflicts) to 409.
 */
@RestControllerAdvice(basePackages = "com.universalplatform.content")
class ContentApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("code", "INVALID_CONTENT_REQUEST", "message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> conflict(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "CONTENT_CONFLICT", "message", exception.getMessage()));
    }
}
