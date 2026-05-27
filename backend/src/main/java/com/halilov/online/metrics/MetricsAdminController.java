package com.halilov.online.metrics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin dashboard counters under {@code /api/admin/metrics}. Single
 * aggregated read for the admin home screen — today's orders, revenue,
 * low-stock count, recent signups, etc.
 */
@RestController
@RequestMapping("/api/admin/metrics")
public class MetricsAdminController {

    private final MetricsService metrics;

    public MetricsAdminController(MetricsService metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/dashboard")
    public MetricsDtos.DashboardMetrics dashboard() {
        return metrics.getDashboard();
    }
}
