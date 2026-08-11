package com.universalplatform.content;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
interface LearningResourceVersionRepository extends JpaRepository<LearningResourceVersion,UUID>{List<LearningResourceVersion> findAllByTenantIdAndResourceIdOrderByVersionNumberDesc(UUID tenantId,UUID resourceId); Optional<LearningResourceVersion> findByTenantIdAndResourceIdAndVersionNumber(UUID tenantId,UUID resourceId,int version);}
