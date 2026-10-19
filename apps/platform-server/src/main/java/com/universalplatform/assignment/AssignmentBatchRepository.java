package com.universalplatform.assignment;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface AssignmentBatchRepository extends JpaRepository<AssignmentBatch,UUID>{ boolean existsByTenantIdAndAssignmentIdAndBatchId(UUID tenantId,UUID assignmentId,UUID batchId); List<AssignmentBatch> findAllByTenantIdAndAssignmentId(UUID tenantId,UUID assignmentId); List<AssignmentBatch> findAllByTenantIdAndBatchId(UUID tenantId,UUID batchId); List<AssignmentBatch> findAllByTenantIdAndBatchIdIn(UUID tenantId,Collection<UUID> batchIds);

 /**
  * Serializes the batch weight-sum check across concurrent assignments (advisory lock held until the
  * surrounding transaction commits, closing the read-then-insert race). Idempotent re-assignment of a
  * batch already linked to this assignment skips the lock path entirely.
  */
 @org.springframework.data.jpa.repository.Query(value = "select pg_advisory_xact_lock(:lockKey)", nativeQuery = true)
 void lockBatchWeight(@org.springframework.data.repository.query.Param("lockKey") long lockKey); }
