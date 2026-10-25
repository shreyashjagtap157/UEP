package com.universalplatform.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agent/swarm")
class SwarmAgentController {
    private final SwarmService service;

    SwarmAgentController(SwarmService service) { this.service = service; }

    @PostMapping("/run")
    ResponseEntity<SwarmService.SwarmResult> run(@Valid @RequestBody RunRequest request) {
        return ResponseEntity.ok(service.run(request.task(), request.agents()));
    }

    record RunRequest(
            @NotBlank @Size(max = 12_000) String task,
            @Min(1) @Max(8) Integer agents) {}
}
