package com.universalplatform.academics;

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

@RestController
@RequestMapping("/api/v1")
class AcademicController {
    private final AcademicService academics;

    AcademicController(AcademicService academics) { this.academics = academics; }

    @GetMapping("/academic-periods")
    AcademicPage<AcademicService.PeriodView> periods(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "25") int size) {
        return academics.listPeriods(page, size);
    }

    @PostMapping("/academic-periods")
    AcademicService.PeriodView createPeriod(@Valid @RequestBody PeriodRequest request) {
        return academics.createPeriod(request.code(), request.displayName(), request.startsOn(), request.endsOn());
    }

    @PutMapping("/academic-periods/{id}")
    AcademicService.PeriodView updatePeriod(@PathVariable UUID id, @Valid @RequestBody PeriodRequest request) {
        if (request.status() == null) throw new IllegalArgumentException("Academic period status is required when updating");
        return academics.updatePeriod(id, request.code(), request.displayName(), request.startsOn(), request.endsOn(), request.status(), request.expectedVersion());
    }

    @GetMapping("/programs")
    AcademicPage<AcademicService.ProgramView> programs(@RequestParam(required = false) UUID academicPeriodId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "25") int size) {
        return academics.listPrograms(academicPeriodId, page, size);
    }

    @PostMapping("/programs")
    AcademicService.ProgramView createProgram(@Valid @RequestBody ProgramRequest request) {
        return academics.createProgram(request.academicPeriodId(), request.code(), request.displayName(), request.description());
    }

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
