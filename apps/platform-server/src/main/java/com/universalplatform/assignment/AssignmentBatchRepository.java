package com.universalplatform.assignment;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface AssignmentBatchRepository extends JpaRepository<AssignmentBatch,UUID>{ boolean existsByTenantIdAndAssignmentIdAndBatchId(UUID tenantId,UUID assignmentId,UUID batchId); List<AssignmentBatch> findAllByTenantIdAndAssignmentId(UUID tenantId,UUID assignmentId); List<AssignmentBatch> findAllByTenantIdAndBatchId(UUID tenantId,UUID batchId); List<AssignmentBatch> findAllByTenantIdAndBatchIdIn(UUID tenantId,Collection<UUID> batchIds); }
