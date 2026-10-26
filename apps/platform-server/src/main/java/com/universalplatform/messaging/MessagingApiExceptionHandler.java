package com.universalplatform.messaging;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.universalplatform.messaging")
class MessagingApiExceptionHandler {
    @ExceptionHandler(MessagingNotFoundException.class)
    ResponseEntity<?> notFound(MessagingNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "MESSAGE_NOT_FOUND", "message", exception.getMessage()));
    }

    @ExceptionHandler({MessagingConflictException.class, ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<?> conflict(RuntimeException exception) {
        String message = exception instanceof MessagingConflictException ? exception.getMessage() : "Request conflicts with the current message state";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "MESSAGE_CONFLICT", "message", message));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<?> invalid(Exception exception) {
        String message = exception instanceof IllegalArgumentException ? exception.getMessage() : "Messaging request validation failed";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("code", "INVALID_MESSAGE_REQUEST", "message", message));
    }
}
