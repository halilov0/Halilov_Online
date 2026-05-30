package com.halilov.online.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Optional daily retention purge of {@code audit_log}, off by default.
 *
 * <p>Controlled by {@code app.audit.retentionDays}:
 * <ul>
 *   <li>{@code 0} (default) — disabled; the job is a no-op and only the
 *       manual admin button can purge.</li>
 *   <li>{@code > 0} — every night at 03:30 server time, rows older than
 *       that many days are removed via
 *       {@link AuditMaintenanceService#purgeOlderThan}.</li>
 * </ul>
 *
 * Deliberately defaults off so a deploy never silently starts deleting the
 * security trail — enabling it is an explicit ops decision.
 */
@Component
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    private final AuditMaintenanceService maintenance;
    private final int retentionDays;

    public AuditRetentionJob(AuditMaintenanceService maintenance,
                             @Value("${app.audit.retentionDays:0}") int retentionDays) {
        this.maintenance = maintenance;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purge() {
        if (retentionDays <= 0) return;
        try {
            long deleted = maintenance.purgeOlderThan(retentionDays, true);
            if (deleted > 0) log.info("audit_log retention purge removed {} rows (keep {} days)", deleted, retentionDays);
        } catch (Exception e) {
            log.warn("audit_log retention purge failed: {}", e.toString());
        }
    }
}
