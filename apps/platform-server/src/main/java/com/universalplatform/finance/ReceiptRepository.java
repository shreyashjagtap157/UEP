package com.universalplatform.finance; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface ReceiptRepository extends JpaRepository<Receipt,UUID>{ Optional<Receipt> findByTenantIdAndPaymentId(UUID tenantId,UUID paymentId); }
