package com.universalplatform.finance; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface InstitutionFeeRepository extends JpaRepository<InstitutionFee,UUID>{ List<InstitutionFee> findAllByTenantIdOrderByName(UUID tenantId); Optional<InstitutionFee> findByTenantIdAndId(UUID tenantId,UUID id); }
