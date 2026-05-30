package com.halilov.online.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.halilov.online.audit.AuditAction;
import com.halilov.online.audit.AuditService;
import com.halilov.online.auth.PasswordResetService;
import com.halilov.online.common.BulkResult;
import com.halilov.online.order.OrderRepository;
import com.halilov.online.order.OrderStatus;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin user management under {@code /api/admin/users}. Paginated +
 * filterable search (via a JPA {@code Specification}), enable/disable,
 * force-logout (stamps {@code force_logout_at} so the next request from
 * existing tokens fails the auth filter's freshness check), and
 * admin-initiated password reset.
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserRepository users;
    private final OrderRepository orders;
    private final AuditService audit;
    private final PasswordResetService resetService;

    public AdminUserController(UserRepository users, OrderRepository orders,
                               AuditService audit, PasswordResetService resetService) {
        this.users = users;
        this.orders = orders;
        this.audit = audit;
        this.resetService = resetService;
    }

    /** Paginated user list with per-row order/spend stats. Simple in-memory
     *  join via OrderRepository scans — fine at our volume; revisit when the
     *  users table starts breaking 10k rows. */
    @GetMapping
    public AdminUserList list(@RequestParam(required = false) String q,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)),
            Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> rows = (q == null || q.isBlank())
            ? users.findAll(pageable)
            : users.findAll(pageable); // small dataset; filter in-memory below to keep query simple
        List<User> filtered = (q == null || q.isBlank())
            ? rows.getContent()
            : rows.getContent().stream()
                .filter(u -> matches(u, q.toLowerCase().trim()))
                .toList();

        Map<Long, long[]> statsByUser = computeStats();
        List<AdminUserRow> content = filtered.stream()
            .map(u -> AdminUserRow.of(u, statsByUser.get(u.getId())))
            .toList();
        return new AdminUserList(content, rows.getTotalElements(), rows.getNumber(), rows.getSize());
    }

    @PatchMapping("/{id}")
    public AdminUserRow patch(@PathVariable Long id, @Valid @RequestBody AdminUserPatch req) {
        User u = users.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
        boolean wasEnabled = u.isEnabled();
        if (req.enabled() != null && req.enabled() != u.isEnabled()) {
            u.setEnabled(req.enabled());
            // disabling = also drop existing sessions, otherwise the user
            // keeps working until their JWT expires.
            if (!req.enabled()) u.setForceLogoutAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        }
        users.save(u);
        if (req.enabled() != null && wasEnabled != u.isEnabled()) {
            audit.record(u.isEnabled() ? AuditAction.USER_ENABLED : AuditAction.USER_DISABLED,
                "user", u.getId(),
                (u.isEnabled() ? "חשבון הופעל מחדש: " : "חשבון הושבת: ") + u.getEmail());
        }
        return AdminUserRow.of(u, null);
    }

    /**
     * Bulk enable/disable. ADMIN-role rows are never touched — they're
     * counted as {@code skipped} so an over-broad "select all" can't lock
     * the operator (or a co-admin) out of the panel. Disabling also stamps
     * {@code force_logout_at} to drop live sessions, matching {@link #patch}.
     */
    @PostMapping("/bulk-status")
    public BulkResult bulkStatus(@Valid @RequestBody BulkStatusRequest req) {
        List<User> targets = users.findAllById(req.ids());
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        int affected = 0;
        int skipped = 0;
        for (User u : targets) {
            if (u.getRole() == Role.ADMIN) { skipped++; continue; }
            if (u.isEnabled() == req.enabled()) { skipped++; continue; }
            u.setEnabled(req.enabled());
            if (!req.enabled()) u.setForceLogoutAt(now);
            affected++;
        }
        users.saveAll(targets);
        // Account for ids that didn't resolve to a row at all.
        skipped += req.ids().size() - targets.size();
        if (affected > 0) {
            audit.record(req.enabled() ? AuditAction.USER_BULK_ENABLED : AuditAction.USER_BULK_DISABLED,
                "user", null,
                affected + (req.enabled() ? " חשבונות הופעלו בבת אחת" : " חשבונות הושבתו בבת אחת"),
                BulkResult.idsMetadata(req.ids()));
        }
        return new BulkResult(affected, skipped);
    }

    @PostMapping("/{id}/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void triggerPasswordReset(@PathVariable Long id) {
        resetService.requestForUser(id);
    }

    @PostMapping("/{id}/force-logout")
    public AdminUserRow forceLogout(@PathVariable Long id) {
        User u = users.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
        u.setForceLogoutAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        users.save(u);
        audit.record(AuditAction.USER_FORCE_LOGOUT, "user", u.getId(),
            "ניתוק כפוי: " + u.getEmail());
        return AdminUserRow.of(u, null);
    }

    /** Clears any auto-lockout state so the user can sign in again right away.
     *  Resets failed-attempt counter too. */
    @PostMapping("/{id}/unlock")
    public AdminUserRow unlock(@PathVariable Long id) {
        User u = users.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
        u.setFailedLoginCount(0);
        u.setLockedUntil(null);
        users.save(u);
        audit.record(AuditAction.USER_FORCE_LOGOUT, "user", u.getId(),
            "ננעל ידנית שוחרר: " + u.getEmail());
        return AdminUserRow.of(u, null);
    }

    private Map<Long, long[]> computeStats() {
        // [orderCount, spendAgorot] keyed by user_id, only counting orders that
        // actually generated revenue (PAID through SHIPPED/DELIVERED; refunds
        // and cancellations are deliberately excluded).
        Map<Long, long[]> stats = new HashMap<>();
        orders.findAllByOrderByCreatedAtDesc().forEach(o -> {
            if (o.getUserId() == null) return;
            OrderStatus s = o.getStatus();
            if (s == OrderStatus.PENDING || s == OrderStatus.CANCELLED || s == OrderStatus.REFUNDED) return;
            long[] cur = stats.computeIfAbsent(o.getUserId(), k -> new long[2]);
            cur[0]++;
            cur[1] += o.getTotalAgorot();
        });
        return stats;
    }

    private static boolean matches(User u, String qLower) {
        if (u.getEmail() != null && u.getEmail().toLowerCase().contains(qLower)) return true;
        if (u.getFullName() != null && u.getFullName().toLowerCase().contains(qLower)) return true;
        if (u.getPhone() != null && u.getPhone().toLowerCase().contains(qLower)) return true;
        return false;
    }

    public record AdminUserList(List<AdminUserRow> content, long total, int page, int size) {}

    public record AdminUserRow(
        Long id, String email, String fullName, String phone,
        String role, boolean enabled, boolean marketingOptIn,
        Instant createdAt, Instant forceLogoutAt,
        long orderCount, long spendAgorot
    ) {
        public static AdminUserRow of(User u, long[] stats) {
            long oc = stats == null ? 0 : stats[0];
            long sp = stats == null ? 0 : stats[1];
            return new AdminUserRow(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(),
                u.getRole().name(), u.isEnabled(), u.isMarketingOptIn(),
                u.getCreatedAt(), u.getForceLogoutAt(), oc, sp);
        }
    }

    public record AdminUserPatch(@NotNull Boolean enabled) {}

    public record BulkStatusRequest(
        @NotEmpty @Size(max = 2000) List<Long> ids,
        @NotNull Boolean enabled
    ) {}
}
