package com.halilov.online.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Stable {@code /api/ping} liveness check. Wraps Actuator with a
 * fixed-path JSON response so Docker healthchecks and nginx upstream
 * probes don't need to know about Actuator's URL layout.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
            "status", "ok",
            "service", "halilov-market",
            "time", Instant.now().toString()
        );
    }
}
