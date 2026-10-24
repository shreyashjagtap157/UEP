package com.universalplatform.finance;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.universalplatform.finance")
class FinanceApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("code", "INVALID_FINANCE_REQUEST", "message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "FINANCE_CONFLICT", "message", exception.getMessage()));
    }

    @ExceptionHandler(PaymentProcessingException.class)
    ResponseEntity<Map<String, String>> paymentFailure(PaymentProcessingException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("code", "PAYMENT_PROCESSING_FAILED",
                        "message", "The provider did not authorize or capture the payment. The failed attempt was recorded."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> duplicateReference(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "FINANCE_CONFLICT",
                        "message", "The finance operation conflicts with an existing settlement or financial record."));
    }
}
