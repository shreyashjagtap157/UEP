package com.universalplatform.finance; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface InstallmentRepository extends JpaRepository<Installment,UUID>{ List<Installment> findAllByTenantIdAndInvoiceIdOrderBySequenceNo(UUID tenantId,UUID invoiceId); Optional<Installment> findByTenantIdAndId(UUID tenantId,UUID id); }
