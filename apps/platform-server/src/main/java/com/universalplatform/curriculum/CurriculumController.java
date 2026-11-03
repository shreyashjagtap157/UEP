package com.universalplatform.curriculum;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Curriculum Management", description = "Endpoints for managing courses, subjects, and curriculum modules")
@RestController
@RequestMapping("/api/v1")
class CurriculumController {
    private final CurriculumService curriculum;

    CurriculumController(CurriculumService curriculum) { this.curriculum = curriculum; }

    @Operation(
            summary = "List courses",
            description = "Retrieves a paginated list of courses, optionally filtered by programId. Accepts optional page and size query parameters."
    )
    @GetMapping("/courses")
    CurriculumPage<CurriculumService.CourseView> courses(@RequestParam(required = false) UUID programId,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "25") int size) {
        return curriculum.listCourses(programId, page, size);
    }

    @Operation(
            summary = "Create course",
            description = "Creates a new course within a curriculum program. Requires a CourseRequest body with code, displayName, and optional programId and description."
    )
    @PostMapping("/courses")
    CurriculumService.CourseView createCourse(@Valid @RequestBody CourseRequest request) {
        return curriculum.createCourse(request.programId(), request.code(), request.displayName(), request.description());
    }

    @Operation(
            summary = "Update course",
            description = "Updates an existing course. Requires the course ID in the path and a CourseRequest body containing status and expectedVersion for optimistic locking."
    )
    @PutMapping("/courses/{id}")
    CurriculumService.CourseView updateCourse(@PathVariable UUID id, @Valid @RequestBody CourseRequest request) {
        if (request.status() == null) throw new IllegalArgumentException("Course status is required when updating");
        return curriculum.updateCourse(id, request.programId(), request.code(), request.displayName(), request.description(), request.status(), request.expectedVersion());
    }

    @Operation(
            summary = "List subjects",
            description = "Retrieves a paginated list of subjects, optionally filtered by courseId. Accepts optional page and size query parameters."
    )
    @GetMapping("/subjects")
    CurriculumPage<CurriculumService.SubjectView> subjects(@RequestParam(required = false) UUID courseId,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "25") int size) {
        return curriculum.listSubjects(courseId, page, size);
    }

    @Operation(
            summary = "Create subject",
            description = "Creates a new subject, optionally linked to a course. Requires a SubjectRequest body with code and displayName."
    )
    @PostMapping("/subjects")
    CurriculumService.SubjectView createSubject(@Valid @RequestBody SubjectRequest request) {
        return curriculum.createSubject(request.courseId(), request.code(), request.displayName(), request.description());
    }

    @Operation(
            summary = "Update subject",
            description = "Updates an existing subject. Requires the subject ID in the path and a SubjectRequest body containing status and expectedVersion for optimistic locking."
    )
    @PutMapping("/subjects/{id}")
    CurriculumService.SubjectView updateSubject(@PathVariable UUID id, @Valid @RequestBody SubjectRequest request) {
        if (request.status() == null) throw new IllegalArgumentException("Subject status is required when updating");
        return curriculum.updateSubject(id, request.courseId(), request.code(), request.displayName(), request.description(), request.status(), request.expectedVersion());
    }

    @Operation(
            summary = "List modules",
            description = "Retrieves a paginated list of modules, optionally filtered by programId, courseId, or subjectId. Accepts optional page and size query parameters."
    )
    @GetMapping("/modules")
    CurriculumPage<CurriculumService.ModuleView> modules(@RequestParam(required = false) UUID programId,
                                                         @RequestParam(required = false) UUID courseId,
                                                         @RequestParam(required = false) UUID subjectId,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "25") int size) {
        return curriculum.listModules(programId, courseId, subjectId, page, size);
    }

    @Operation(
            summary = "Create module",
            description = "Creates a new module under a program, course, or subject. Requires a ModuleRequest body with code, displayName, and optional sequenceNumber."
    )
    @PostMapping("/modules")
    CurriculumService.ModuleView createModule(@Valid @RequestBody ModuleRequest request) {
        return curriculum.createModule(request.programId(), request.courseId(), request.subjectId(), request.code(), request.displayName(), request.description(), request.sequenceNumber());
    }

    @Operation(
            summary = "Update module",
            description = "Updates an existing module. Requires the module ID in the path and a ModuleRequest body containing status and expectedVersion for optimistic locking."
    )
    @PutMapping("/modules/{id}")
    CurriculumService.ModuleView updateModule(@PathVariable UUID id, @Valid @RequestBody ModuleRequest request) {
        if (request.status() == null) throw new IllegalArgumentException("Module status is required when updating");
        return curriculum.updateModule(id, request.programId(), request.courseId(), request.subjectId(), request.code(), request.displayName(), request.description(), request.sequenceNumber(), request.status(), request.expectedVersion());
    }

    record CourseRequest(UUID programId, @NotBlank @Size(max = 64) String code,
                         @NotBlank @Size(max = 200) String displayName, @Size(max = 4000) String description,
                         CurriculumStatus status, long expectedVersion) {}
    record SubjectRequest(UUID courseId, @NotBlank @Size(max = 64) String code,
                          @NotBlank @Size(max = 200) String displayName, @Size(max = 4000) String description,
                          CurriculumStatus status, long expectedVersion) {}
    record ModuleRequest(UUID programId, UUID courseId, UUID subjectId,
                         @NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 200) String displayName,
                         @Size(max = 4000) String description, @Positive Integer sequenceNumber,
                         CurriculumStatus status, long expectedVersion) {}
}
