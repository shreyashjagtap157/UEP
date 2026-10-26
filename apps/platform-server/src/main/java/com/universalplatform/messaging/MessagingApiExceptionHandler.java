package com.universalplatform.messaging;

import com.universalplatform.identity.IdentityAccessDeniedException;
import com.universalplatform.identity.IdentityNotFoundException;
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
    @ExceptionHandler({MessagingNotFoundException.class, IdentityNotFoundException.class})
    ResponseEntity<?> notFound(RuntimeException exception) {
        if (exception instanceof IdentityNotFoundException) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "code", "MESSAGE_PARTICIPANT_NOT_FOUND",
                    "message", "A participant membership was not found in the authenticated tenant"));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "code", "MESSAGE_NOT_FOUND",
                "message", exception.getMessage()));
    }

    @ExceptionHandler(IdentityAccessDeniedException.class)
    ResponseEntity<?> accessDenied(IdentityAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "code", "MESSAGING_ACCESS_DENIED",
                "message", "Messaging requires active memberships in the authenticated tenant"));
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
