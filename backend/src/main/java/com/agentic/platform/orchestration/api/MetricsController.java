package com.agentic.platform.orchestration.api;

import com.agentic.platform.orchestration.service.MetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Orchestration metrics API (public read). All values derive from persisted state. */
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping
    public Map<String, Object> snapshot() {
        return metricsService.snapshot();
    }
}
