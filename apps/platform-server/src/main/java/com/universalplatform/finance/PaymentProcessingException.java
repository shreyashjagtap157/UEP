package com.universalplatform.finance;

/** A provider-declared authorization/capture failure whose FAILED payment row must commit. */
final class PaymentProcessingException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    PaymentProcessingException(String message) {
        super(message);
    }
}
