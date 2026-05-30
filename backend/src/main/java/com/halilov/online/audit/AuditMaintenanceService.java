package com.halilov.online.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Retention maintenance for the append-only {@code audit_log}.
 *
 * <p>The trail is intentionally append-only and tamper-evident (hourly R2
 * snapshots), so there is deliberately <em>no</em> "delete everything" or
 * delete-by-id path. The only removal allowed is a retention purge of rows
 * older than a cutoff, and the purge itself writes one
 * {@link AuditAction#AUDIT_LOG_PURGED} row — which, being newer than any
 * cutoff, always survives. That leaves a breadcrumb of every purge.
 *
 * <p>Used by both the manual admin button
 * ({@code DELETE /api/admin/audit-log}) and the optional scheduled job
 * ({@link AuditRetentionJob}).
 */
@Service
public class AuditMaintenanceService {

    /** Floor on the retention window. Even a manual purge must leave at
     *  least the last day of activity — you can never wipe "today". */
    public static final int MIN_RETENTION_DAYS = 1;

    private final AuditLogRepository repo;
    private final AuditService audit;

    public AuditMaintenanceService(AuditLogRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    /**
     * Delete audit rows older than {@code retentionDays} days and record the
     * purge. The {@code AUDIT_LOG_PURGED} marker is written after the delete
     * so it is never itself purged.
     *
     * @param retentionDays keep this many days; clamped up to
     *        {@link #MIN_RETENTION_DAYS}
     * @param scheduled     {@code true} when fired by the cron job, {@code false}
     *        for a manual admin request — recorded in the audit message
     * @return number of rows removed
     */
    @Transactional
    public long purgeOlderThan(int retentionDays, boolean scheduled) {
        int days = Math.max(MIN_RETENTION_DAYS, retentionDays);
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        int deleted = repo.deleteOlderThan(cutoff);
        audit.record(AuditAction.AUDIT_LOG_PURGED, "audit_log", null,
            (scheduled ? "ניקוי אוטומטי של לוג: " : "ניקוי ידני של לוג: ")
                + deleted + " רשומות ישנות מ-" + days + " ימים נמחקו",
            "{\"retentionDays\":" + days + ",\"deleted\":" + deleted
                + ",\"scheduled\":" + scheduled + "}");
        return deleted;
    }
}
