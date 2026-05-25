package com.halilov.online.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-log")
public class AdminAuditLogController {

    private final AuditLogRepository repo;

    public AdminAuditLogController(AuditLogRepository repo) {
        this.repo = repo;
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

    private static Instant parseTs(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Instant.parse(s); } catch (Exception ignored) {}
        // also accept yyyy-MM-dd for convenience from the UI date picker
        try { return java.time.LocalDate.parse(s).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(); } catch (Exception ignored) {}
        return null;
    }

    public record AuditLogList(List<AuditLogRow> content, long total, int page, int size) {}

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
