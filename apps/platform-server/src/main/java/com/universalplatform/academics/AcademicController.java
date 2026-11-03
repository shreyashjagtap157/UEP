package com.universalplatform.academics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Academics", description = "Endpoints for managing academic periods and educational programs")
@RestController
@RequestMapping("/api/v1")
class AcademicController {
    private final AcademicService academics;

    AcademicController(AcademicService academics) { this.academics = academics; }

    @Operation(
            summary = "List academic periods",
            description = "Retrieves a paginated list of academic periods. Accepts optional page and size query parameters."
    )
    @GetMapping("/academic-periods")
    AcademicPage<AcademicService.PeriodView> periods(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "25") int size) {
        return academics.listPeriods(page, size);
    }

    @Operation(
            summary = "Create academic period",
            description = "Creates a new academic period. Requires a PeriodRequest body with code, displayName, startsOn date, and endsOn date."
    )
    @PostMapping("/academic-periods")
    AcademicService.PeriodView createPeriod(@Valid @RequestBody PeriodRequest request) {
        return academics.createPeriod(request.code(), request.displayName(), request.startsOn(), request.endsOn());
    }

    @Operation(
            summary = "Update academic period",
            description = "Updates an existing academic period's schedule and status. Requires the period ID in the path and a PeriodRequest body with status and expectedVersion for optimistic locking."
    )
    @PutMapping("/academic-periods/{id}")
    AcademicService.PeriodView updatePeriod(@PathVariable UUID id, @Valid @RequestBody PeriodRequest request) {
        if (request.status() == null) throw new IllegalArgumentException("Academic period status is required when updating");
        return academics.updatePeriod(id, request.code(), request.displayName(), request.startsOn(), request.endsOn(), request.status(), request.expectedVersion());
    }

    @Operation(
            summary = "List programs",
            description = "Retrieves a paginated list of programs, optionally filtered by academicPeriodId. Accepts optional page and size query parameters."
    )
    @GetMapping("/programs")
    AcademicPage<AcademicService.ProgramView> programs(@RequestParam(required = false) UUID academicPeriodId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "25") int size) {
        return academics.listPrograms(academicPeriodId, page, size);
    }

    @Operation(
            summary = "Create program",
            description = "Creates a new program optionally linked to an academic period. Requires a ProgramRequest body containing code and displayName."
    )
    @PostMapping("/programs")
    AcademicService.ProgramView createProgram(@Valid @RequestBody ProgramRequest request) {
        return academics.createProgram(request.academicPeriodId(), request.code(), request.displayName(), request.description());
    }

    @Operation(
            summary = "Update program",
            description = "Updates an existing program's details and status. Requires the program ID in the path and a ProgramRequest body with status and expectedVersion for optimistic locking."
    )
    @PutMapping("/programs/{id}")
    AcademicService.ProgramView updateProgram(@PathVariable UUID id, @Valid @RequestBody ProgramRequest request) {
        if (request.status() == null) throw new IllegalArgumentException("Program status is required when updating");
        return academics.updateProgram(id, request.academicPeriodId(), request.code(), request.displayName(), request.description(), request.status(), request.expectedVersion());
    }

    record PeriodRequest(@NotBlank @Size(max = 64) String code,
                         @NotBlank @Size(max = 200) String displayName,
                         @NotNull LocalDate startsOn,
                         @NotNull LocalDate endsOn,
                         AcademicPeriodStatus status,
                         long expectedVersion) {}

    record ProgramRequest(UUID academicPeriodId,
                          @NotBlank @Size(max = 64) String code,
                          @NotBlank @Size(max = 200) String displayName,
                          @Size(max = 2000) String description,
                          ProgramStatus status,
                          long expectedVersion) {}
}
