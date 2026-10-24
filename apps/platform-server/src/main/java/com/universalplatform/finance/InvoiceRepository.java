package com.universalplatform.finance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByTenantIdAndId(UUID tenantId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.tenantId = :tenantId and i.id = :invoiceId")
    Optional<Invoice> lockByTenantIdAndId(@Param("tenantId") UUID tenantId,
                                          @Param("invoiceId") UUID invoiceId);

    Page<Invoice> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
    List<Invoice> findAllByTenantIdAndMembershipIdOrderByCreatedAtDesc(UUID tenantId, UUID membershipId);
}
