package com.universalplatform.assignment;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface RubricRepository extends JpaRepository<Rubric,UUID>{ Optional<Rubric> findByTenantIdAndAssignmentId(UUID tenantId,UUID assignmentId); }
