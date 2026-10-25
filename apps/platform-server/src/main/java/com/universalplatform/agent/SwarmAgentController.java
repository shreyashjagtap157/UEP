package com.universalplatform.agent;

import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
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
    private final AuthorizationService authorization;

    SwarmAgentController(SwarmService service, AuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @PostMapping("/run")
    ResponseEntity<SwarmService.SwarmResult> run(@Valid @RequestBody RunRequest request) {
        authorization.require(PermissionKey.OPERATIONS_MANAGE);
        return ResponseEntity.ok(service.run(request.task(), request.agents()));
    }

    record RunRequest(
            @NotBlank @Size(max = 12_000) String task,
            @Min(1) @Max(8) Integer agents) {}
}
