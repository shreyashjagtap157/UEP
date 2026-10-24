package com.universalplatform.finance;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class ManualPaymentProvider implements PaymentProvider {
    @Override
    public String name() {
        return "MANUAL";
    }

    @Override
    public PaymentResult authorize(UUID id, BigDecimal amount, String currency, String settlementReference) {
        String reference = settlementReference == null ? "" : settlementReference.trim();
        if (reference.isEmpty() || reference.length() > 120
                || reference.chars().anyMatch(Character::isISOControl)) {
            return new PaymentResult(false, null, "A valid external settlement reference is required");
        }
        return new PaymentResult(true, "MANUAL:" + reference,
                "Operator-verified settlement reference recorded; no external payment gateway was contacted");
    }

    @Override
    public PaymentResult capture(String providerReference) {
        if (providerReference == null || !providerReference.startsWith("MANUAL:")
                || providerReference.substring("MANUAL:".length()).isBlank()) {
            return new PaymentResult(false, providerReference, "Manual settlement reference is invalid");
        }
        return new PaymentResult(true, providerReference, "Operator-verified manual settlement recorded");
    }

    @Override
    public PaymentResult refund(String providerReference) {
        return new PaymentResult(false, providerReference,
                "Manual refunds require an explicit reconciliation workflow and are not automated");
    }
}
