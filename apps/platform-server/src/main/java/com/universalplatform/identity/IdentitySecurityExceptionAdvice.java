package com.universalplatform.identity;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class IdentitySecurityExceptionAdvice {
    @ExceptionHandler(IdentityAccessDeniedException.class)
    ResponseEntity<Map<String, String>> forbidden(IdentityAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("code", "IDENTITY_ACCESS_DENIED", "message", exception.getMessage()));
    }
}
