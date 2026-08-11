package com.universalplatform.curriculum;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.universalplatform.curriculum")
class CurriculumApiExceptionHandler {
    @ExceptionHandler(CurriculumNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(CurriculumNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "CURRICULUM_RESOURCE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler({CurriculumConflictException.class, ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<Map<String, String>> conflict(RuntimeException exception) {
        String message = exception instanceof CurriculumConflictException ? exception.getMessage() : "Request conflicts with the current curriculum state";
        return response(HttpStatus.CONFLICT, "CURRICULUM_CONFLICT", message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_CURRICULUM_REQUEST", exception.getMessage());
    }

    private static ResponseEntity<Map<String, String>> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }
}
