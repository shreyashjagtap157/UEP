package com.universalplatform.content;
import java.util.Optional; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
interface StorageObjectRepository extends JpaRepository<StorageObject,UUID>{Optional<StorageObject> findByTenantIdAndId(UUID tenantId,UUID id);}
