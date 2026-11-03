package com.universalplatform.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "Current workspace overview and daily scheduled activities")
@RestController
@RequestMapping("/api/v1/today")
class TodayController {
    private final TodayService service;
    TodayController(TodayService service) { this.service = service; }
        @Operation(summary = "Get today overview", description = "Retrieves aggregated counts of classes, examinations, institutional operations, and notifications scheduled for today.")
    @GetMapping TodayService.TodayView today() { return service.today(); }
}
