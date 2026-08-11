package com.universalplatform.academics;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.universalplatform.academics")
class AcademicApiExceptionHandler {
    @ExceptionHandler(AcademicNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(AcademicNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "ACADEMIC_RESOURCE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler({AcademicConflictException.class, ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<Map<String, String>> conflict(RuntimeException exception) {
        String message = exception instanceof AcademicConflictException ? exception.getMessage() : "Request conflicts with the current academic state";
        return response(HttpStatus.CONFLICT, "ACADEMIC_CONFLICT", message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_ACADEMIC_REQUEST", exception.getMessage());
    }

    private static ResponseEntity<Map<String, String>> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }
}
