package com.universalplatform.curriculum;

import com.universalplatform.security.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit read-only curriculum boundary used by enrollment and later scheduling modules. */
@Service
public class CurriculumDirectory {
    private final TenantContext tenantContext;
    private final CourseRepository courses;
    private final SubjectRepository subjects;
    private final CurriculumModuleRepository modules;

    CurriculumDirectory(TenantContext tenantContext, CourseRepository courses, SubjectRepository subjects, CurriculumModuleRepository modules) {
        this.tenantContext = tenantContext;
        this.courses = courses;
        this.subjects = subjects;
        this.modules = modules;
    }

    @Transactional(readOnly = true)
    public CourseReference requireCourse(UUID id) {
        Course course = courses.findByTenantIdAndId(tenantContext.requireTenantId(), id)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));
        return new CourseReference(course.id(), course.programId(), course.code(), course.displayName(), course.status());
    }

    @Transactional(readOnly = true)
    public SubjectReference requireSubject(UUID id) {
        Subject subject = subjects.findByTenantIdAndId(tenantContext.requireTenantId(), id)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));
        return new SubjectReference(subject.id(), subject.courseId(), subject.code(), subject.displayName(), subject.status());
    }

    @Transactional(readOnly = true)
    public ModuleReference requireModule(UUID id) {
        CurriculumModule module = modules.findByTenantIdAndId(tenantContext.requireTenantId(), id)
                .orElseThrow(() -> new IllegalArgumentException("Module not found"));
        return new ModuleReference(module.id(), module.programId(), module.courseId(), module.subjectId(), module.code(), module.displayName(), module.status());
    }

    public record CourseReference(UUID id, UUID programId, String code, String displayName, CurriculumStatus status) {}
    public record SubjectReference(UUID id, UUID courseId, String code, String displayName, CurriculumStatus status) {}
    public record ModuleReference(UUID id, UUID programId, UUID courseId, UUID subjectId, String code, String displayName, CurriculumStatus status) {}
}
