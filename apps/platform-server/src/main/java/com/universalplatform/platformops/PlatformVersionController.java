package com.universalplatform.platformops;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
final class PlatformVersionController {
    private final String version;

    PlatformVersionController(@Value("${platform.version}") String version) {
        this.version = version;
    }

    @GetMapping("/version")
    Map<String, String> version() {
        return Map.of("version", version, "releaseStatus", version.endsWith("-SNAPSHOT") ? "SNAPSHOT" : "RELEASE");
    }
}
