package com.universalplatform.finance;
import java.math.BigDecimal; import java.util.UUID;
public interface PaymentProvider { String name(); PaymentResult authorize(UUID paymentId,BigDecimal amount,String currency); PaymentResult capture(String providerReference); PaymentResult refund(String providerReference); record PaymentResult(boolean success,String reference,String message){} }
