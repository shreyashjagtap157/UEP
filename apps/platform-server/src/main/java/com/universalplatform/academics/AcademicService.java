package com.universalplatform.academics;

import com.universalplatform.audit.AuditService;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AcademicService {
    private final TenantContext tenantContext;
    private final ActorContext actorContext;
    private final AuthorizationService authorization;
    private final AcademicPeriodRepository periods;
    private final AcademicProgramRepository programs;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    AcademicService(TenantContext tenantContext, ActorContext actorContext, AuthorizationService authorization,
                    AcademicPeriodRepository periods, AcademicProgramRepository programs, AuditService audit) {
        this.tenantContext = tenantContext;
        this.actorContext = actorContext;
        this.authorization = authorization;
        this.periods = periods;
        this.programs = programs;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    AcademicPage<PeriodView> listPeriods(int page, int size) {
        authorization.require(PermissionKey.ACADEMICS_VIEW);
        UUID tenantId = tenantContext.requireTenantId();
        Page<AcademicPeriod> result = periods.findAllByTenantId(tenantId,
                PageRequest.of(safePage(page), safeSize(size), Sort.by(Sort.Direction.DESC, "startsOn").and(Sort.by("id"))));
        return page(result, AcademicService::view);
    }

    @Transactional
    PeriodView createPeriod(String code, String displayName, LocalDate startsOn, LocalDate endsOn) {
        authorization.require(PermissionKey.ACADEMICS_MANAGE);
        validateDates(startsOn, endsOn);
        UUID tenantId = tenantContext.requireTenantId();
        String normalizedCode = code(code);
        if (periods.existsByTenantIdAndCodeIgnoreCase(tenantId, normalizedCode)) throw new AcademicConflictException("Academic period code already exists");
        AcademicPeriod period = periods.save(new AcademicPeriod(UUID.randomUUID(), tenantId, normalizedCode,
                required(displayName, "Academic period name", 200), startsOn, endsOn, clock.instant()));
        audit.record(tenantId, actorContext.requireSubject(), "ACADEMIC_PERIOD_CREATED", "academic_period", period.id().toString());
        return view(period);
    }

    @Transactional
    PeriodView updatePeriod(UUID id, String code, String displayName, LocalDate startsOn, LocalDate endsOn,
                            AcademicPeriodStatus status, long expectedVersion) {
        authorization.require(PermissionKey.ACADEMICS_MANAGE);
        validateDates(startsOn, endsOn);
        UUID tenantId = tenantContext.requireTenantId();
        AcademicPeriod period = period(tenantId, id);
        if (period.version() != expectedVersion) throw new AcademicConflictException("Academic period was modified by another request");
        String normalizedCode = code(code);
        if (!period.code().equalsIgnoreCase(normalizedCode) && periods.existsByTenantIdAndCodeIgnoreCase(tenantId, normalizedCode)) {
            throw new AcademicConflictException("Academic period code already exists");
        }
        period.update(normalizedCode, required(displayName, "Academic period name", 200), startsOn, endsOn, status, clock.instant());
        audit.record(tenantId, actorContext.requireSubject(), "ACADEMIC_PERIOD_UPDATED", "academic_period", period.id().toString());
        return view(period);
    }

    @Transactional(readOnly = true)
    AcademicPage<ProgramView> listPrograms(UUID academicPeriodId, int page, int size) {
        authorization.require(PermissionKey.ACADEMICS_VIEW);
        UUID tenantId = tenantContext.requireTenantId();
        PageRequest pageable = PageRequest.of(safePage(page), safeSize(size), Sort.by(Sort.Direction.ASC, "displayName", "id"));
        Page<AcademicProgram> result = academicPeriodId == null
                ? programs.findAllByTenantId(tenantId, pageable)
                : programs.findAllByTenantIdAndAcademicPeriodId(tenantId, academicPeriodId, pageable);
        return page(result, AcademicService::view);
    }

    @Transactional
    ProgramView createProgram(UUID academicPeriodId, String code, String displayName, String description) {
        authorization.require(PermissionKey.ACADEMICS_MANAGE);
        UUID tenantId = tenantContext.requireTenantId();
        if (academicPeriodId != null) period(tenantId, academicPeriodId);
        String normalizedCode = code(code);
        if (programs.existsByTenantIdAndCodeIgnoreCase(tenantId, normalizedCode)) throw new AcademicConflictException("Program code already exists");
        AcademicProgram program = programs.save(new AcademicProgram(UUID.randomUUID(), tenantId, academicPeriodId, normalizedCode,
                required(displayName, "Program name", 200), nullable(description, 2000), clock.instant()));
        audit.record(tenantId, actorContext.requireSubject(), "ACADEMIC_PROGRAM_CREATED", "academic_program", program.id().toString());
        return view(program);
    }

    @Transactional
    ProgramView updateProgram(UUID id, UUID academicPeriodId, String code, String displayName, String description,
                              ProgramStatus status, long expectedVersion) {
        authorization.require(PermissionKey.ACADEMICS_MANAGE);
        UUID tenantId = tenantContext.requireTenantId();
        if (academicPeriodId != null) period(tenantId, academicPeriodId);
        AcademicProgram program = program(tenantId, id);
        if (program.version() != expectedVersion) throw new AcademicConflictException("Program was modified by another request");
        String normalizedCode = code(code);
        if (!program.code().equalsIgnoreCase(normalizedCode) && programs.existsByTenantIdAndCodeIgnoreCase(tenantId, normalizedCode)) {
            throw new AcademicConflictException("Program code already exists");
        }
        program.update(academicPeriodId, normalizedCode, required(displayName, "Program name", 200), nullable(description, 2000), status, clock.instant());
        audit.record(tenantId, actorContext.requireSubject(), "ACADEMIC_PROGRAM_UPDATED", "academic_program", program.id().toString());
        return view(program);
    }

    private AcademicPeriod period(UUID tenantId, UUID id) {
        return periods.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new AcademicNotFoundException("Academic period not found"));
    }

    private AcademicProgram program(UUID tenantId, UUID id) {
        return programs.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new AcademicNotFoundException("Program not found"));
    }

    private static void validateDates(LocalDate startsOn, LocalDate endsOn) {
        if (startsOn == null || endsOn == null) throw new IllegalArgumentException("Academic period dates are required");
        if (endsOn.isBefore(startsOn)) throw new IllegalArgumentException("Academic period end date cannot be before start date");
    }

    private static String code(String value) {
        String normalized = required(value, "Code", 64).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9][A-Z0-9._-]{0,63}")) throw new IllegalArgumentException("Code may contain letters, numbers, dot, underscore, and hyphen");
        return normalized;
    }

    static String required(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(label + " is too long");
        return normalized;
    }

    static String nullable(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("Value is too long");
        return normalized;
    }

    private static int safePage(int page) { return Math.max(0, page); }
    private static int safeSize(int size) { return size < 1 ? 25 : Math.min(size, 100); }

    private static <E, V> AcademicPage<V> page(Page<E> page, java.util.function.Function<E, V> mapper) {
        return new AcademicPage<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private static PeriodView view(AcademicPeriod period) {
        return new PeriodView(period.id(), period.code(), period.displayName(), period.startsOn(), period.endsOn(), period.status(), period.createdAt(), period.version());
    }

    private static ProgramView view(AcademicProgram program) {
        return new ProgramView(program.id(), program.academicPeriodId(), program.code(), program.displayName(), program.description(), program.status(), program.createdAt(), program.version());
    }

    record PeriodView(UUID id, String code, String displayName, LocalDate startsOn, LocalDate endsOn,
                      AcademicPeriodStatus status, java.time.Instant createdAt, long version) {}
    record ProgramView(UUID id, UUID academicPeriodId, String code, String displayName, String description,
                       ProgramStatus status, java.time.Instant createdAt, long version) {}
}
