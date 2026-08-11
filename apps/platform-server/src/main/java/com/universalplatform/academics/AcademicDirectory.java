package com.universalplatform.academics;

import com.universalplatform.security.TenantContext;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit read-only academic boundary used by curriculum and enrollment modules. */
@Service
public class AcademicDirectory {
    private final TenantContext tenantContext;
    private final AcademicPeriodRepository periods;
    private final AcademicProgramRepository programs;

    AcademicDirectory(TenantContext tenantContext, AcademicPeriodRepository periods, AcademicProgramRepository programs) {
        this.tenantContext = tenantContext;
        this.periods = periods;
        this.programs = programs;
    }

    @Transactional(readOnly = true)
    public PeriodReference requirePeriod(UUID id) {
        UUID tenantId = tenantContext.requireTenantId();
        AcademicPeriod period = periods.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Academic period not found"));
        return new PeriodReference(period.id(), period.code(), period.displayName(), period.startsOn(), period.endsOn(), period.status());
    }

    @Transactional(readOnly = true)
    public ProgramReference requireProgram(UUID id) {
        UUID tenantId = tenantContext.requireTenantId();
        AcademicProgram program = programs.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Program not found"));
        return new ProgramReference(program.id(), program.academicPeriodId(), program.code(), program.displayName(), program.status());
    }

    public record PeriodReference(UUID id, String code, String displayName, LocalDate startsOn, LocalDate endsOn, AcademicPeriodStatus status) {}
    public record ProgramReference(UUID id, UUID academicPeriodId, String code, String displayName, ProgramStatus status) {}
}
