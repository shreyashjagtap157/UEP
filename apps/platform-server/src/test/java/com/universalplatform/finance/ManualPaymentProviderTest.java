package com.universalplatform.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManualPaymentProviderTest {
    private final ManualPaymentProvider provider = new ManualPaymentProvider();

    @Test
    void refusesAuthorizationWithoutExplicitSettlementReference() {
        PaymentProvider.PaymentResult result = provider.authorize(
                UUID.randomUUID(), new BigDecimal("25.00"), "USD", "  ");

        assertThat(result.success()).isFalse();
        assertThat(result.reference()).isNull();
        assertThat(result.message()).contains("external settlement reference is required");
    }

    @Test
    void recordsManualSettlementReferenceAndCapturesOnlyThatReference() {
        PaymentProvider.PaymentResult authorized = provider.authorize(
                UUID.randomUUID(), new BigDecimal("25.00"), "USD", "bank-txn-20261024-01");

        assertThat(authorized.success()).isTrue();
        assertThat(authorized.reference()).isEqualTo("MANUAL:bank-txn-20261024-01");

        PaymentProvider.PaymentResult captured = provider.capture(authorized.reference());
        assertThat(captured.success()).isTrue();
        assertThat(captured.reference()).isEqualTo(authorized.reference());
        assertThat(captured.message()).contains("Operator-verified");
    }

    @Test
    void doesNotReportAutomatedManualRefundAsSuccessful() {
        PaymentProvider.PaymentResult result = provider.refund("MANUAL:bank-txn-20261024-01");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("not automated");
    }
}
