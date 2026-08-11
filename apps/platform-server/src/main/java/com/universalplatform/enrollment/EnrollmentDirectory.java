package com.universalplatform.enrollment;

import com.universalplatform.security.TenantContext;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit read-only batch boundary for later scheduling and live-class modules. */
@Service
public class EnrollmentDirectory {
    private final TenantContext tenantContext;
    private final BatchRepository batches;

    EnrollmentDirectory(TenantContext tenantContext, BatchRepository batches) {
        this.tenantContext = tenantContext;
        this.batches = batches;
    }

    @Transactional(readOnly = true)
    public BatchReference requireBatch(UUID id) {
        Batch batch = batches.findByTenantIdAndId(tenantContext.requireTenantId(), id)
                .orElseThrow(() -> new EnrollmentNotFoundException("Batch not found"));
        return new BatchReference(batch.id(), batch.academicPeriodId(), batch.programId(), batch.courseId(),
                batch.branchId(), batch.code(), batch.displayName(), batch.startsOn(), batch.endsOn(), batch.status());
    }

    public record BatchReference(UUID id, UUID academicPeriodId, UUID programId, UUID courseId, UUID branchId,
                                 String code, String displayName, LocalDate startsOn, LocalDate endsOn, BatchStatus status) {}
}
