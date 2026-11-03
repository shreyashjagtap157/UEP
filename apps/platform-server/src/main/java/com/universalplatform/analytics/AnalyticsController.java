package com.universalplatform.analytics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Analytics", description = "Endpoints for academic overview, attendance metrics, assessment outcomes, operations, usage trends, and CSV exports")
@RestController
@RequestMapping("/api/v1/analytics")
class AnalyticsController {
    private final AnalyticsService service;

    AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @Operation(
        summary = "Get analytics overview",
        description = "Retrieves high-level workspace analytics summary. Accepts optional from and to Instant query parameters for date range filtering."
    )
    @GetMapping("/overview")
    AnalyticsService.Overview overview(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        return service.overview(defaultFrom(from), defaultTo(to));
    }

    @Operation(
        summary = "Get attendance analytics",
        description = "Retrieves attendance reports across classes and sessions. Accepts optional from and to Instant query parameters for date range filtering."
    )
    @GetMapping("/attendance")
    AnalyticsService.AttendanceReport attendance(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        return service.attendance(defaultFrom(from), defaultTo(to));
    }

    @Operation(
        summary = "Get assessment analytics",
        description = "Retrieves performance outcomes and assessment completion metrics. Accepts optional from and to Instant query parameters for date range filtering."
    )
    @GetMapping("/assessments")
    AnalyticsService.AssessmentReport assessments(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        return service.assessments(defaultFrom(from), defaultTo(to));
    }

    @Operation(
        summary = "Get question analytics",
        description = "Retrieves item-level question difficulty and discrimination metrics. Accepts optional from and to Instant query parameters for date range filtering."
    )
    @GetMapping("/questions")
    AnalyticsService.QuestionReport questions(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        return service.questions(defaultFrom(from), defaultTo(to));
    }

    @Operation(
        summary = "Get operational analytics",
        description = "Retrieves platform operational performance, job dispatch, and error rates. Accepts optional from and to Instant query parameters for date range filtering."
    )
    @GetMapping("/operations")
    AnalyticsService.OperationsReport operations(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        return service.operations(defaultFrom(from), defaultTo(to));
    }

    @Operation(
        summary = "Get usage analytics",
        description = "Retrieves workspace resource usage trends, storage consumption, and active membership statistics."
    )
    @GetMapping("/usage")
    AnalyticsService.UsageReport usage() {
        return service.usage();
    }

    @Operation(
        summary = "Get analytics forecast",
        description = "Retrieves trend projections and predictive capacity forecasts for the workspace."
    )
    @GetMapping("/forecast")
    AnalyticsService.ForecastReport forecast() {
        return service.forecast();
    }

    @Operation(
        summary = "Get session recording analytics",
        description = "Retrieves metrics on class session recording views and duration. Accepts optional from and to Instant query parameters for date range filtering."
    )
    @GetMapping("/recordings")
    AnalyticsService.RecordingReport recordings(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        return service.recordings(defaultFrom(from), defaultTo(to));
    }

    @Operation(
        summary = "Get financial analytics",
        description = "Retrieves revenue, invoice settlement, and outstanding fee analytics. Accepts optional from and to Instant query parameters for date range filtering."
    )
    @GetMapping("/finance")
    AnalyticsService.FinanceReport finance(@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        return service.finance(defaultFrom(from), defaultTo(to));
    }

    @Operation(
        summary = "Export analytics report as CSV",
        description = "Exports a specified analytics report (e.g. overview, attendance, assessments) formatted as CSV. Requires report name path variable and accepts optional from and to Instant query parameters."
    )
    @GetMapping(value = "/exports/{report}", produces = "text/csv")
    ResponseEntity<String> export(@PathVariable String report, @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=uep-" + report + "-report.csv")
                .body(service.export(report, defaultFrom(from), defaultTo(to)));
    }

    private static Instant defaultTo(Instant i) {
        return i == null ? Instant.now() : i;
    }

    private static Instant defaultFrom(Instant i) {
        return i == null ? defaultTo(null).minus(30, ChronoUnit.DAYS) : i;
    }
}
