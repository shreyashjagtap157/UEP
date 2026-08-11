package com.universalplatform.scheduling;

import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.universalplatform.scheduling")
class SchedulingApiExceptionHandler {
    @ExceptionHandler(SchedulingNotFoundException.class)
    ResponseEntity<?> notFound(SchedulingNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "SCHEDULE_RESOURCE_NOT_FOUND", "message", exception.getMessage()));
    }

    @ExceptionHandler(ScheduleConflictDetectedException.class)
    ResponseEntity<?> conflicts(ScheduleConflictDetectedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ConflictResponse("SCHEDULE_CONFLICT", exception.getMessage(), exception.conflicts()));
    }

    @ExceptionHandler({SchedulingConflictException.class, ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<?> conflict(RuntimeException exception) {
        String message = exception instanceof SchedulingConflictException ? exception.getMessage() : "Request conflicts with the current schedule state";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", "SCHEDULE_CONFLICT", "message", message));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<?> invalid(Exception exception) {
        String message = exception instanceof IllegalArgumentException ? exception.getMessage() : "Schedule request validation failed";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("code", "INVALID_SCHEDULE_REQUEST", "message", message));
    }

    record ConflictResponse(String code, String message, List<ScheduleConflict> conflicts) {}
}
