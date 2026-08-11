package com.universalplatform.security;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
final class ApiExceptionHandler {
    @ExceptionHandler({MissingTenantContextException.class, MissingActorContextException.class})
    ResponseEntity<Map<String, String>> missingContext(RuntimeException exception) {
        String code = exception instanceof MissingTenantContextException
                ? "TENANT_CONTEXT_REQUIRED"
                : "ACTOR_CONTEXT_REQUIRED";
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("code", code, "message", exception.getMessage()));
    }
}
