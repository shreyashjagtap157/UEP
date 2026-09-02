package com.universalplatform.finance; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface PaymentRepository extends JpaRepository<Payment,UUID>{ Optional<Payment> findByTenantIdAndId(UUID tenantId,UUID id); List<Payment> findAllByTenantIdAndInvoiceIdOrderByCreatedAtDesc(UUID tenantId,UUID invoiceId); }
