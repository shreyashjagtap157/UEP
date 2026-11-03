package com.universalplatform.platformops;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Platform Operations", description = "Platform runtime health and version metadata")
@RestController
@RequestMapping("/api/v1/platform")
class PlatformVersionController {
    private final String version;

    PlatformVersionController(@Value("${platform.version}") String version) {
        this.version = version;
    }

        @Operation(summary = "Get platform version", description = "Returns the active platform semantic version and release status.")
    @GetMapping("/version")
    Map<String, String> version() {
        return Map.of("version", version, "releaseStatus", version.endsWith("-SNAPSHOT") ? "SNAPSHOT" : "RELEASE");
    }
}
