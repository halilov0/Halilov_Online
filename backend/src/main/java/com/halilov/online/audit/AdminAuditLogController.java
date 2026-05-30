package com.halilov.online.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.halilov.online.common.Csv;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-only admin window onto {@code audit_log} under
 * {@code /api/admin/audit-log}: filtering by actor user id, action code,
 * and timestamp range, paginated up to 500 rows per page, plus a filtered
 * CSV export.
 *
 * <p>The only mutation exposed is a retention purge
 * ({@code DELETE /api/admin/audit-log?olderThanDays=N}) — there is no
 * delete-by-id or wipe-all, because the trail is append-only and
 * tamper-evident. See {@link AuditMaintenanceService}.
 */
@RestController
@RequestMapping("/api/admin/audit-log")
public class AdminAuditLogController {

    /** Hard cap on CSV export rows, so an export can't pull an unbounded set. */
    private static final int EXPORT_MAX_ROWS = 50_000;

    private final AuditLogRepository repo;
    private final AuditMaintenanceService maintenance;

    public AdminAuditLogController(AuditLogRepository repo, AuditMaintenanceService maintenance) {
        this.repo = repo;
        this.maintenance = maintenance;
    }

    @GetMapping
    public AuditLogList list(@RequestParam(required = false) Long userId,
                             @RequestParam(required = false) String action,
                             @RequestParam(required = false) String from,
                             @RequestParam(required = false) String to,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "100") int size) {
        Instant fromTs = parseTs(from);
        Instant toTs = parseTs(to);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(500, Math.max(1, size)));
        Page<AuditLog> rows = repo.search(
            userId,
            (action == null || action.isBlank()) ? null : action,
            fromTs, toTs, pageable);
        List<AuditLogRow> content = rows.getContent().stream().map(AuditLogRow::of).toList();
        return new AuditLogList(content, rows.getTotalElements(), rows.getNumber(), rows.getSize());
    }

    @GetMapping("/actions")
    public List<String> actions() {
        return repo.distinctActions();
    }

    /** Filtered CSV export — same filters as {@link #list}, capped at
     *  {@value #EXPORT_MAX_ROWS} rows. Acts as a backup before a purge. */
    @GetMapping(value = "/export.csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<String> exportCsv(@RequestParam(required = false) Long userId,
                                            @RequestParam(required = false) String action,
                                            @RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to) {
        Pageable pageable = PageRequest.of(0, EXPORT_MAX_ROWS);
        Page<AuditLog> rows = repo.search(
            userId,
            (action == null || action.isBlank()) ? null : action,
            parseTs(from), parseTs(to), pageable);

        StringBuilder out = new StringBuilder(Csv.BOM);
        out.append(Csv.row("createdAt", "action", "actorEmail", "actorRole",
            "actorIp", "targetType", "targetId", "message"));
        for (AuditLog a : rows.getContent()) {
            out.append(Csv.row(a.getCreatedAt(), a.getAction(), a.getActorEmail(),
                a.getActorRole(), a.getActorIp(), a.getTargetType(), a.getTargetId(), a.getMessage()));
        }
        String filename = "audit-log-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(out.toString());
    }

    /**
     * Retention purge: remove rows older than {@code olderThanDays} days.
     * Deliberately the only delete path — there is no wipe-all and no
     * delete-by-id, and the purge writes its own audit row. Rejects a window
     * below {@link AuditMaintenanceService#MIN_RETENTION_DAYS} so "today"
     * can never be erased.
     */
    @DeleteMapping
    public PurgeResult purge(@RequestParam int olderThanDays) {
        if (olderThanDays < AuditMaintenanceService.MIN_RETENTION_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "olderThanDays must be >= " + AuditMaintenanceService.MIN_RETENTION_DAYS);
        }
        long deleted = maintenance.purgeOlderThan(olderThanDays, false);
        return new PurgeResult(deleted, olderThanDays);
    }

    private static Instant parseTs(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Instant.parse(s); } catch (Exception ignored) {}
        // also accept yyyy-MM-dd for convenience from the UI date picker
        try { return java.time.LocalDate.parse(s).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(); } catch (Exception ignored) {}
        return null;
    }

    public record AuditLogList(List<AuditLogRow> content, long total, int page, int size) {}

    public record PurgeResult(long deleted, int olderThanDays) {}

    public record AuditLogRow(
        Long id, Long actorUserId, String actorEmail, String actorRole, String actorIp,
        String action, String targetType, String targetId, String message, String metadata,
        Instant createdAt
    ) {
        public static AuditLogRow of(AuditLog a) {
            return new AuditLogRow(a.getId(), a.getActorUserId(), a.getActorEmail(),
                a.getActorRole(), a.getActorIp(), a.getAction(),
                a.getTargetType(), a.getTargetId(), a.getMessage(), a.getMetadata(),
                a.getCreatedAt());
        }
    }
}
