package com.universalplatform.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.universalplatform.audit.AuditService;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.MembershipDirectory;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.licensing.EntitlementDecision;
import com.universalplatform.licensing.EntitlementService;
import com.universalplatform.licensing.FeatureKey;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class FinancePaymentFailureStateTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID invoiceId = UUID.randomUUID();
    private final UUID membershipId = UUID.randomUUID();

    @Mock TenantContext tenants;
    @Mock ActorContext actors;
    @Mock AuthorizationService auth;
    @Mock MembershipDirectory memberships;
    @Mock EntitlementService entitlements;
    @Mock InstitutionFeeRepository fees;
    @Mock InvoiceRepository invoices;
    @Mock InstallmentRepository installments;
    @Mock PaymentRepository payments;
    @Mock ReceiptRepository receipts;
    @Mock AuditService audit;
    @Mock PaymentProvider provider;

    private FinanceService service;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        service = new FinanceService(tenants, actors, auth, memberships, entitlements, fees, invoices,
                installments, payments, receipts, audit, provider);
        invoice = new Invoice(invoiceId, tenantId, membershipId, "INV-TEST-01",
                new BigDecimal("100.00"), "USD", LocalDate.now().plusDays(7), Instant.now());
        invoice.issue(LocalDate.now());
        lenient().when(tenants.requireTenantId()).thenReturn(tenantId);
        lenient().when(invoices.lockByTenantIdAndId(tenantId, invoiceId)).thenReturn(Optional.of(invoice));
        lenient().when(entitlements.decide(tenantId, FeatureKey.PAYMENTS)).thenReturn(EntitlementDecision.allow(FeatureKey.PAYMENTS));
        lenient().when(provider.name()).thenReturn("TEST_PROVIDER");
        lenient().when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void authorizationDeclineRetainsFailedPaymentStateAndDoesNotIssueReceipt() {
        when(provider.authorize(any(UUID.class), any(BigDecimal.class), any(String.class), any(String.class)))
                .thenReturn(new PaymentProvider.PaymentResult(false, null, "declined"));

        assertThatThrownBy(() -> service.pay(invoiceId, new BigDecimal("25.00"), "USD",
                "bank-reference-1", true))
                .isInstanceOf(PaymentProcessingException.class)
                .hasMessage("Payment authorization failed");

        Payment failed = savedPayment();
        assertThat(failed.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.failureReason()).isEqualTo("declined");
        verify(receipts, never()).save(any(Receipt.class));
        verify(invoices, never()).save(any(Invoice.class));
    }

    @Test
    void captureDeclineRetainsFailedAuthorizedPaymentAndDoesNotUpdateInvoice() {
        when(provider.authorize(any(UUID.class), any(BigDecimal.class), any(String.class), any(String.class)))
                .thenReturn(new PaymentProvider.PaymentResult(true, "provider-reference", "authorized"));
        when(provider.capture("provider-reference"))
                .thenReturn(new PaymentProvider.PaymentResult(false, "provider-reference", "capture rejected"));
        when(actors.requireSubject()).thenReturn("finance-operator");

        assertThatThrownBy(() -> service.pay(invoiceId, new BigDecimal("25.00"), "USD",
                "bank-reference-2", true))
                .isInstanceOf(PaymentProcessingException.class)
                .hasMessage("Payment capture failed");

        Payment failed = savedPayment();
        assertThat(failed.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.providerReference()).isEqualTo("provider-reference");
        assertThat(failed.failureReason()).isEqualTo("capture rejected");
        assertThat(invoice.paidAmount()).isEqualByComparingTo("0.00");
        verify(receipts, never()).save(any(Receipt.class));
        verify(invoices, never()).save(any(Invoice.class));
    }

    @Test
    void providerFailureExceptionsAreConfiguredNotToRollBackTheFailedPaymentRecord() throws Exception {
        Transactional transactional = FinanceService.class.getDeclaredMethod("pay", UUID.class, BigDecimal.class,
                String.class, String.class, boolean.class).getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.noRollbackFor()).contains(PaymentProcessingException.class);
    }

    private Payment savedPayment() {
        ArgumentCaptor<Payment> capture = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(capture.capture());
        return capture.getValue();
    }
}
