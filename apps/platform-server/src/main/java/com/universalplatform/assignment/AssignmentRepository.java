package com.universalplatform.assignment;
import java.util.*; import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface AssignmentRepository extends JpaRepository<Assignment,UUID>{ Optional<Assignment> findByTenantIdAndId(UUID tenantId,UUID id); Page<Assignment> findAllByTenantId(UUID tenantId,Pageable p); List<Assignment> findAllByTenantIdAndIdIn(UUID tenantId,Collection<UUID> ids); Page<Assignment> findAllByTenantIdAndStatusInAndIdIn(UUID tenantId,Collection<AssignmentStatus> statuses,Collection<UUID> ids,Pageable pageable); }
