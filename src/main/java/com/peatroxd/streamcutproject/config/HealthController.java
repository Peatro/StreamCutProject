package com.peatroxd.streamcutproject.config;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final WorkerDiagnosticsService workerDiagnosticsService;

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping(value = "/health/ready", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> readiness() {
        return Map.of("status", "READY");
    }

    @GetMapping(value = "/health/workers", produces = MediaType.APPLICATION_JSON_VALUE)
    public WorkerDiagnosticsService.WorkerDiagnosticsResponse workerDiagnostics() {
        return workerDiagnosticsService.snapshot();
    }
}
