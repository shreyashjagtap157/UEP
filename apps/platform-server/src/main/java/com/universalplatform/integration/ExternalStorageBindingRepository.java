package com.universalplatform.integration;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface ExternalStorageBindingRepository extends JpaRepository<ExternalStorageBinding,UUID>{Optional<ExternalStorageBinding> findByTenantId(UUID tenantId);}
