package com.universalplatform.analytics;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
class AnalyticsController {
    private final AnalyticsService service;
    AnalyticsController(AnalyticsService service){this.service=service;}
    @GetMapping("/overview") AnalyticsService.Overview overview(@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){return service.overview(defaultFrom(from),defaultTo(to));}
    @GetMapping("/attendance") AnalyticsService.AttendanceReport attendance(@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){return service.attendance(defaultFrom(from),defaultTo(to));}
    @GetMapping("/assessments") AnalyticsService.AssessmentReport assessments(@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){return service.assessments(defaultFrom(from),defaultTo(to));}
    @GetMapping("/questions") AnalyticsService.QuestionReport questions(@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){return service.questions(defaultFrom(from),defaultTo(to));}
    @GetMapping("/operations") AnalyticsService.OperationsReport operations(@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){return service.operations(defaultFrom(from),defaultTo(to));}
    @GetMapping("/usage") AnalyticsService.UsageReport usage(){return service.usage();}
    @GetMapping("/forecast") AnalyticsService.ForecastReport forecast(){return service.forecast();}
    @GetMapping("/recordings") AnalyticsService.RecordingReport recordings(@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){return service.recordings(defaultFrom(from),defaultTo(to));}
    @GetMapping("/finance") AnalyticsService.FinanceReport finance(@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){return service.finance(defaultFrom(from),defaultTo(to));}
    @GetMapping(value="/exports/{report}",produces="text/csv") ResponseEntity<String> export(@PathVariable String report,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=uep-"+report+"-report.csv").body(service.export(report,defaultFrom(from),defaultTo(to)));}
    private static Instant defaultTo(Instant i){return i==null?Instant.now():i;} private static Instant defaultFrom(Instant i){return i==null?defaultTo(null).minus(30,ChronoUnit.DAYS):i;}
}
