package com.universalplatform.communication;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.universalplatform.communication")
class CommunicationApiExceptionHandler {
    @ExceptionHandler(CommunicationNotFoundException.class)
    ResponseEntity<?> notFound(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "ANNOUNCEMENT_NOT_FOUND", "message", exception.getMessage()));
    }

    @ExceptionHandler({CommunicationConflictException.class, ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<?> conflict(RuntimeException exception) {
        String message = exception instanceof CommunicationConflictException ? exception.getMessage() : "Request conflicts with the current announcement state";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "ANNOUNCEMENT_CONFLICT", "message", message));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<?> invalid(Exception exception) {
        String message = exception instanceof IllegalArgumentException ? exception.getMessage() : "Announcement request validation failed";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("code", "INVALID_ANNOUNCEMENT_REQUEST", "message", message));
    }
}
