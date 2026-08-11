package com.universalplatform.curriculum;

import com.universalplatform.academics.AcademicDirectory;
import com.universalplatform.audit.AuditService;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CurriculumService {
    private final TenantContext tenantContext;
    private final ActorContext actorContext;
    private final AuthorizationService authorization;
    private final AcademicDirectory academics;
    private final CourseRepository courses;
    private final SubjectRepository subjects;
    private final CurriculumModuleRepository modules;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    CurriculumService(TenantContext tenantContext, ActorContext actorContext, AuthorizationService authorization,
                      AcademicDirectory academics, CourseRepository courses, SubjectRepository subjects,
                      CurriculumModuleRepository modules, AuditService audit) {
        this.tenantContext = tenantContext;
        this.actorContext = actorContext;
        this.authorization = authorization;
        this.academics = academics;
        this.courses = courses;
        this.subjects = subjects;
        this.modules = modules;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    CurriculumPage<CourseView> listCourses(UUID programId, int page, int size) {
        authorization.require(PermissionKey.CURRICULUM_VIEW);
        UUID tenantId = tenantContext.requireTenantId();
        PageRequest pageable = pageable(page, size);
        Page<Course> result = programId == null ? courses.findAllByTenantId(tenantId, pageable)
                : courses.findAllByTenantIdAndProgramId(tenantId, programId, pageable);
        return page(result, CurriculumService::view);
    }

    @Transactional
    CourseView createCourse(UUID programId, String code, String displayName, String description) {
        authorization.require(PermissionKey.CURRICULUM_MANAGE);
        UUID tenantId = tenantContext.requireTenantId();
        if (programId != null) academics.requireProgram(programId);
        String normalizedCode = code(code);
        if (courses.existsByTenantIdAndCodeIgnoreCase(tenantId, normalizedCode)) throw new CurriculumConflictException("Course code already exists");
        Course course = courses.save(new Course(UUID.randomUUID(), tenantId, programId, normalizedCode,
                required(displayName, "Course name", 200), nullable(description, 4000), clock.instant()));
        audit.record(tenantId, actorContext.requireSubject(), "COURSE_CREATED", "course", course.id().toString());
        return view(course);
    }

    @Transactional
    CourseView updateCourse(UUID id, UUID programId, String code, String displayName, String description,
                            CurriculumStatus status, long expectedVersion) {
        authorization.require(PermissionKey.CURRICULUM_MANAGE);
        UUID tenantId = tenantContext.requireTenantId();
        if (programId != null) academics.requireProgram(programId);
        Course course = course(tenantId, id);
        if (course.version() != expectedVersion) throw new CurriculumConflictException("Course was modified by another request");
        String normalizedCode = code(code);
        if (!course.code().equalsIgnoreCase(normalizedCode) && courses.existsByTenantIdAndCodeIgnoreCase(tenantId, normalizedCode)) throw new CurriculumConflictException("Course code already exists");
        course.update(programId, normalizedCode, required(displayName, "Course name", 200), nullable(description, 4000), status, clock.instant());
        audit.record(tenantId, actorContext.requireSubject(), "COURSE_UPDATED", "course", course.id().toString());
        return view(course);
    }

    @Transactional(readOnly = true)
    CurriculumPage<SubjectView> listSubjects(UUID courseId, int page, int size) {
        authorization.require(PermissionKey.CURRICULUM_VIEW);
        UUID tenantId = tenantContext.requireTenantId();
        PageRequest pageable = pageable(page, size);
        Page<Subject> result = courseId == null ? subjects.findAllByTenantId(tenantId, pageable)
                : subjects.findAllByTenantIdAndCourseId(tenantId, courseId, pageable);
        return page(result, CurriculumService::view);
    }

    @Transactional
    SubjectView createSubject(UUID courseId, String code, String displayName, String description) {
        authorization.require(PermissionKey.CURRICULUM_MANAGE);
        UUID tenantId = tenantContext.requireTenantId();
        if (courseId != null) course(tenantId, courseId);
        String normalizedCode = code(code);
        if (subjects.existsByTenantIdAndCodeIgnoreCase(tenantId, normalizedCode)) throw new CurriculumConflictException("Subject code already exists");
        Subject subject = subjects.save(new Subject(UUID.randomUUID(), tenantId, courseId, normalizedCode,
                required(displayName, "Subject name", 200), nullable(description, 4000), clock.instant()));
        audit.record(tenantId, actorContext.requireSubject(), "SUBJECT_CREATED", "subject", subject.id().toString());
        return view(subject);
    }

    @Transactional
    SubjectView updateSubject(UUID id, UUID courseId, String code, String displayName, String description,
                              CurriculumStatus status, long expectedVersion) {
        authorization.require(PermissionKey.CURRICULUM_MANAGE);
        UUID tenantId = tenantContext.requireTenantId();
        if (courseId != null) course(tenantId, courseId);
        Subject subject = subject(tenantId, id);
        if (subject.version() != expectedVersion) throw new CurriculumConflictException("Subject was modified by another request");
        String normalizedCode = code(code);
        if (!subject.code().equalsIgnoreCase(normalizedCode) && subjects.existsByTenantIdAndCodeIgnoreCase(tenantId, normalizedCode)) throw new CurriculumConflictException("Subject code already exists");
        subject.update(courseId, normalizedCode, required(displayName, "Subject name", 200), nullable(description, 4000), status, clock.instant());
        audit.record(tenantId, actorContext.requireSubject(), "SUBJECT_UPDATED", "subject", subject.id().toString());
        return view(subject);
    }

    @Transactional(readOnly = true)
    CurriculumPage<ModuleView> listModules(UUID programId, UUID courseId, UUID subjectId, int page, int size) {
        authorization.require(PermissionKey.CURRICULUM_VIEW);
        ensureAtMostOneParent(programId, courseId, subjectId);
        UUID tenantId = tenantContext.requireTenantId();
        PageRequest pageable = pageable(page, size);
        Page<CurriculumModule> result = programId != null ? modules.findAllByTenantIdAndProgramId(tenantId, programId, pageable)
                : courseId != null ? modules.findAllByTenantIdAndCourseId(tenantId, courseId, pageable)
                : subjectId != null ? modules.findAllByTenantIdAndSubjectId(tenantId, subjectId, pageable)
                : modules.findAllByTenantId(tenantId, pageable);
        return page(result, CurriculumService::view);
    }

    @Transactional
    ModuleView createModule(UUID programId, UUID courseId, UUID subjectId, String code, String displayName,
                            String description, Integer sequenceNumber) {
        authorization.require(PermissionKey.CURRICULUM_MANAGE);
        validateParent(programId, courseId, subjectId);
        validateSequence(sequenceNumber);
        UUID tenantId = tenantContext.requireTenantId();
        String normalizedCode = code(code);
        if (modules.existsByTenantIdAndCodeIgnoreCase(tenantId, normalizedCode)) throw new CurriculumConflictException("Module code already exists");
        CurriculumModule module = modules.save(new CurriculumModule(UUID.randomUUID(), tenantId, programId, courseId, subjectId,
                normalizedCode, required(displayName, "Module name", 200), nullable(description, 4000), sequenceNumber, clock.instant()));
        audit.record(tenantId, actorContext.requireSubject(), "CURRICULUM_MODULE_CREATED", "curriculum_module", module.id().toString());
        return view(module);
    }

    @Transactional
    ModuleView updateModule(UUID id, UUID programId, UUID courseId, UUID subjectId, String code, String displayName,
                            String description, Integer sequenceNumber, CurriculumStatus status, long expectedVersion) {
        authorization.require(PermissionKey.CURRICULUM_MANAGE);
        validateParent(programId, courseId, subjectId);
        validateSequence(sequenceNumber);
        UUID tenantId = tenantContext.requireTenantId();
        CurriculumModule module = module(tenantId, id);
        if (module.version() != expectedVersion) throw new CurriculumConflictException("Module was modified by another request");
        String normalizedCode = code(code);
        if (!module.code().equalsIgnoreCase(normalizedCode) && modules.existsByTenantIdAndCodeIgnoreCase(tenantId, normalizedCode)) throw new CurriculumConflictException("Module code already exists");
        module.update(programId, courseId, subjectId, normalizedCode, required(displayName, "Module name", 200), nullable(description, 4000), sequenceNumber, status, clock.instant());
        audit.record(tenantId, actorContext.requireSubject(), "CURRICULUM_MODULE_UPDATED", "curriculum_module", module.id().toString());
        return view(module);
    }

    private void validateParent(UUID programId, UUID courseId, UUID subjectId) {
        ensureAtMostOneParent(programId, courseId, subjectId);
        UUID tenantId = tenantContext.requireTenantId();
        if (programId != null) academics.requireProgram(programId);
        if (courseId != null) course(tenantId, courseId);
        if (subjectId != null) subject(tenantId, subjectId);
    }

    private static void ensureAtMostOneParent(UUID programId, UUID courseId, UUID subjectId) {
        int count = (programId == null ? 0 : 1) + (courseId == null ? 0 : 1) + (subjectId == null ? 0 : 1);
        if (count > 1) throw new IllegalArgumentException("A module can have at most one direct parent");
    }

    private static void validateSequence(Integer value) {
        if (value != null && value < 1) throw new IllegalArgumentException("Module sequence number must be positive");
    }

    private Course course(UUID tenantId, UUID id) { return courses.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new CurriculumNotFoundException("Course not found")); }
    private Subject subject(UUID tenantId, UUID id) { return subjects.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new CurriculumNotFoundException("Subject not found")); }
    private CurriculumModule module(UUID tenantId, UUID id) { return modules.findByTenantIdAndId(tenantId, id).orElseThrow(() -> new CurriculumNotFoundException("Module not found")); }

    private static PageRequest pageable(int page, int size) { return PageRequest.of(Math.max(0, page), size < 1 ? 25 : Math.min(size, 100), Sort.by(Sort.Direction.ASC, "displayName", "id")); }
    private static <E, V> CurriculumPage<V> page(Page<E> page, Function<E, V> mapper) { return new CurriculumPage<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()); }

    private static String code(String value) {
        String normalized = required(value, "Code", 64).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9][A-Z0-9._-]{0,63}")) throw new IllegalArgumentException("Code may contain letters, numbers, dot, underscore, and hyphen");
        return normalized;
    }
    private static String required(String value, String label, int max) { if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required"); String normalized = value.trim(); if (normalized.length() > max) throw new IllegalArgumentException(label + " is too long"); return normalized; }
    private static String nullable(String value, int max) { if (value == null || value.isBlank()) return null; String normalized = value.trim(); if (normalized.length() > max) throw new IllegalArgumentException("Value is too long"); return normalized; }

    private static CourseView view(Course course) { return new CourseView(course.id(), course.programId(), course.code(), course.displayName(), course.description(), course.status(), course.createdAt(), course.version()); }
    private static SubjectView view(Subject subject) { return new SubjectView(subject.id(), subject.courseId(), subject.code(), subject.displayName(), subject.description(), subject.status(), subject.createdAt(), subject.version()); }
    private static ModuleView view(CurriculumModule module) { return new ModuleView(module.id(), module.programId(), module.courseId(), module.subjectId(), module.code(), module.displayName(), module.description(), module.sequenceNumber(), module.status(), module.createdAt(), module.version()); }

    record CourseView(UUID id, UUID programId, String code, String displayName, String description, CurriculumStatus status, java.time.Instant createdAt, long version) {}
    record SubjectView(UUID id, UUID courseId, String code, String displayName, String description, CurriculumStatus status, java.time.Instant createdAt, long version) {}
    record ModuleView(UUID id, UUID programId, UUID courseId, UUID subjectId, String code, String displayName, String description, Integer sequenceNumber, CurriculumStatus status, java.time.Instant createdAt, long version) {}
}
