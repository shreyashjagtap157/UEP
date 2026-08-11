package com.universalplatform.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/today")
class TodayController {
    private final TodayService service;
    TodayController(TodayService service) { this.service = service; }
    @GetMapping TodayService.TodayView today() { return service.today(); }
}
