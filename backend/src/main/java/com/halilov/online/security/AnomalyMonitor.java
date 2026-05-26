package com.halilov.online.security;

import com.halilov.online.audit.AuditAction;
import com.halilov.online.audit.AuditLogRepository;
import com.halilov.online.audit.AuditLogRepository.ScopeCount;
import com.halilov.online.notification.EmailMessage;
import com.halilov.online.notification.EmailService;
import com.halilov.online.user.User;
import com.halilov.online.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Periodic scan of {@code audit_log} for known attack signatures. Emails the
 * admin on each detected anomaly with enough context to act:
 * <ul>
 *   <li>Failed logins per account &ge; {@value #FAILED_LOGIN_ACCOUNT_THRESHOLD}/hr —
 *       targeted attack on one user (often paired with account lockout).</li>
 *   <li>Failed logins per IP &ge; {@value #FAILED_LOGIN_IP_THRESHOLD}/hr —
 *       credential stuffing or brute force across many accounts. This is the
 *       real signal: per-account fires rarely against fake emails / locked
 *       accounts, per-IP catches the attacker regardless.</li>
 *   <li>Successful registrations per IP &ge; {@value #REGISTRATION_THRESHOLD}/hr —
 *       automated sign-up abuse.</li>
 *   <li>Cancellations &ge; {@value #CANCELLATION_THRESHOLD} in 5 minutes —
 *       potential exploitation of the cancel/refund flow.</li>
 * </ul>
 * Per-(kind, scope) dedupe via {@code anomaly_alerts} table: once fired, the
 * same scope is suppressed for {@link #ALERT_COOLDOWN}. A sustained attack
 * re-alerts at the cooldown boundary — quiet enough not to flood, frequent
 * enough to confirm "still happening".
 */
@Component
public class AnomalyMonitor {

    private static final Logger log = LoggerFactory.getLogger(AnomalyMonitor.class);

    static final long FAILED_LOGIN_ACCOUNT_THRESHOLD = 5;
    static final long FAILED_LOGIN_IP_THRESHOLD = 20;
    static final long REGISTRATION_THRESHOLD = 5;
    static final long CANCELLATION_THRESHOLD = 50;

    private static final Duration FAILED_LOGIN_WINDOW = Duration.ofHours(1);
    private static final Duration REGISTRATION_WINDOW = Duration.ofHours(1);
    private static final Duration CANCELLATION_WINDOW = Duration.ofMinutes(5);
    private static final Duration ALERT_COOLDOWN = Duration.ofHours(1);

    private static final int SAMPLE_SIZE = 5;

    private final AuditLogRepository audit;
    private final AnomalyAlertRepository state;
    private final UserRepository users;
    private final EmailService emailService;
    private final String alertEmail;
    private final String siteBaseUrl;

    public AnomalyMonitor(AuditLogRepository audit,
                          AnomalyAlertRepository state,
                          UserRepository users,
                          EmailService emailService,
                          @Value("${app.security.alertEmail:${app.email.adminBcc:}}") String alertEmail,
                          @Value("${app.email.siteBaseUrl:}") String siteBaseUrl) {
        this.audit = audit;
        this.state = state;
        this.users = users;
        this.emailService = emailService;
        this.alertEmail = alertEmail == null ? "" : alertEmail.trim();
        this.siteBaseUrl = siteBaseUrl == null ? "" : siteBaseUrl.trim();
    }

    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void scan() {
        if (alertEmail.isBlank()) return;
        runSafely("failed-login-by-account", this::scanFailedLoginsByAccount);
        runSafely("failed-login-by-ip",      this::scanFailedLoginsByIp);
        runSafely("registration-abuse",      this::scanRegistrationAbuse);
        runSafely("cancellation-spike",      this::scanCancellationSpike);
    }

    private void runSafely(String label, Runnable r) {
        try { r.run(); } catch (Exception e) { log.warn("[anomaly] {} scan errored: {}", label, e.toString()); }
    }

    // ---------- detectors ----------

    private void scanFailedLoginsByAccount() {
        Instant since = Instant.now().minus(FAILED_LOGIN_WINDOW);
        for (var row : audit.groupByActorEmailSince(AuditAction.USER_LOGIN_FAILED, since, FAILED_LOGIN_ACCOUNT_THRESHOLD)) {
            String email = row.getScope();
            String key = "FAILED_LOGINS:" + email;
            if (!claim(key)) continue;

            User user = users.findByEmail(email).orElse(null);
            List<ScopeCount> topIps = audit.topIpsForEmail(AuditAction.USER_LOGIN_FAILED, email, since, SAMPLE_SIZE);
            long successes = audit.countActionForEmailSince(AuditAction.USER_LOGIN, email, since);

            String headline = "Failed-login burst on <b>" + esc(email) + "</b>";
            StringBuilder b = new StringBuilder();
            b.append(facts(
                kv("Attempts in last hour", row.getCnt() + " (threshold " + FAILED_LOGIN_ACCOUNT_THRESHOLD + ")"),
                kv("Account exists?", user == null
                    ? "<b>No</b> — likely bot probing nonexistent emails. Not actionable per-account; check IPs."
                    : describeUser(user)),
                kv("Successful logins on this account in window", successes == 0
                    ? "0 (no breach yet)"
                    : "<b>" + successes + " — CREDENTIAL POSSIBLY LEAKED</b>")
            ));
            b.append(listSection("Source IPs (top " + SAMPLE_SIZE + ")", topIps,
                sc -> esc(sc.getScope()) + " — " + sc.getCnt() + " attempts"));
            b.append(playbook(
                "If account exists and any login succeeded above — assume credential leak. "
                    + "Force-logout the user from <a href=\"" + adminUrl("/admin/users") + "\">Admin → Users</a> "
                    + "and trigger a password reset.",
                "If account is locked (see above) — the lockout is doing its job; no action unless attack continues.",
                "If a single IP dominates and is unfamiliar — block at "
                    + "<a href=\"https://dash.cloudflare.com\">Cloudflare → Security → WAF → Custom Rules</a>."
            ));
            sendAlert("Failed logins on " + email, headline, b.toString(), key);
        }
    }

    private void scanFailedLoginsByIp() {
        Instant since = Instant.now().minus(FAILED_LOGIN_WINDOW);
        for (var row : audit.groupByActorIpSince(AuditAction.USER_LOGIN_FAILED, since, FAILED_LOGIN_IP_THRESHOLD)) {
            String ip = row.getScope();
            String key = "FAILED_LOGINS_IP:" + ip;
            if (!claim(key)) continue;

            List<ScopeCount> topAccounts = audit.topEmailsForIp(AuditAction.USER_LOGIN_FAILED, ip, since, SAMPLE_SIZE);
            long successes = audit.countActionForIpSince(AuditAction.USER_LOGIN, ip, since);

            String headline = "Brute-force / credential-stuffing from IP <b>" + esc(ip) + "</b>";
            StringBuilder b = new StringBuilder();
            b.append(facts(
                kv("Failed attempts in last hour", row.getCnt() + " (threshold " + FAILED_LOGIN_IP_THRESHOLD + ")"),
                kv("Distinct accounts targeted", String.valueOf(topAccounts.size()) + (topAccounts.size() >= SAMPLE_SIZE ? "+ (truncated)" : "")),
                kv("Successful logins from this IP in window", successes == 0
                    ? "0 (no breach)"
                    : "<b>" + successes + " — ACCOUNT(S) COMPROMISED from this IP</b>")
            ));
            b.append(listSection("Top targeted accounts", topAccounts,
                sc -> esc(sc.getScope()) + " — " + sc.getCnt() + " attempts"));
            b.append(playbook(
                "<b>First move:</b> block this IP at "
                    + "<a href=\"https://dash.cloudflare.com\">Cloudflare → Security → WAF → Custom Rules</a>. "
                    + "Halilov edge sees a CF-Connecting-IP, so the block must live at CF, not nginx.",
                "If <b>any</b> successful login from this IP appears above — that account is compromised. "
                    + "Force-logout and password-reset it immediately.",
                "If many distinct accounts were targeted with low per-account counts — credential stuffing "
                    + "with a leaked list. Consider mass-notifying users to rotate passwords."
            ));
            sendAlert("Brute force from " + ip, headline, b.toString(), key);
        }
    }

    private void scanRegistrationAbuse() {
        Instant since = Instant.now().minus(REGISTRATION_WINDOW);
        for (var row : audit.groupByActorIpSince(AuditAction.USER_REGISTER, since, REGISTRATION_THRESHOLD)) {
            String ip = row.getScope();
            String key = "REGISTER_ABUSE:" + ip;
            if (!claim(key)) continue;

            List<ScopeCount> recentEmails = audit.topEmailsForIp(AuditAction.USER_REGISTER, ip, since, SAMPLE_SIZE);

            String headline = "Sign-up abuse from IP <b>" + esc(ip) + "</b>";
            StringBuilder b = new StringBuilder();
            b.append(facts(
                kv("Registrations in last hour", row.getCnt() + " (threshold " + REGISTRATION_THRESHOLD + ")"),
                kv("Distinct emails", String.valueOf(recentEmails.size()) + (recentEmails.size() >= SAMPLE_SIZE ? "+ (truncated)" : ""))
            ));
            b.append(listSection("Sample emails registered", recentEmails,
                sc -> esc(sc.getScope())));
            b.append(playbook(
                "Block the IP at <a href=\"https://dash.cloudflare.com\">Cloudflare → Security → WAF</a>.",
                "Bulk-disable the abusive accounts from <a href=\"" + adminUrl("/admin/users") + "\">Admin → Users</a> — "
                    + "they almost certainly won't order, and they pollute marketing metrics.",
                "If this becomes a pattern (multiple IPs over days), add a CAPTCHA on /register. "
                    + "Currently deferred per design — not worth the DX cost for the volume we see."
            ));
            sendAlert("Sign-up abuse from " + ip, headline, b.toString(), key);
        }
    }

    private void scanCancellationSpike() {
        Instant since = Instant.now().minus(CANCELLATION_WINDOW);
        long cnt = audit.countActionSince(AuditAction.ORDER_CANCELLED, since);
        if (cnt < CANCELLATION_THRESHOLD) return;
        String key = "CANCEL_SPIKE";
        if (!claim(key)) return;

        List<ScopeCount> topActors = audit.topActorsForAction(AuditAction.ORDER_CANCELLED, since, SAMPLE_SIZE);

        String headline = "Cancellation spike — <b>" + cnt + "</b> cancellations in last 5 min";
        StringBuilder b = new StringBuilder();
        b.append(facts(
            kv("Cancellations in last 5 min", cnt + " (threshold " + CANCELLATION_THRESHOLD + ")"),
            kv("Distinct actors", String.valueOf(topActors.size()))
        ));
        b.append(listSection("Top cancellers", topActors,
            sc -> esc(sc.getScope()) + " — " + sc.getCnt() + " cancellations"));
        b.append(playbook(
            "If one actor dominates — open their order history at <a href=\"" + adminUrl("/admin/orders") + "\">Admin → Orders</a>; "
                + "look for a refund-flow exploit (cancel-after-paid loops, etc).",
            "If many actors spike together — likely external (sale gone wrong, payment provider outage). "
                + "Check the shop frontend for visible breakage.",
            "If exploit suspected, temporarily flip the order-cancel endpoint off via env or admin toggle."
        ));
        sendAlert("Cancellation spike", headline, b.toString(), key);
    }

    // ---------- dedupe ----------

    private boolean claim(String alertKey) {
        var existing = state.findById(alertKey).orElse(null);
        Instant now = Instant.now();
        if (existing != null && existing.getLastFiredAt().isAfter(now.minus(ALERT_COOLDOWN))) {
            return false;
        }
        if (existing == null) {
            state.save(new AnomalyAlertState(alertKey, now));
        } else {
            existing.setLastFiredAt(now);
            state.save(existing);
        }
        return true;
    }

    // ---------- email ----------

    private void sendAlert(String subject, String headlineHtml, String bodyHtml, String key) {
        String html = """
            <p style="font-size:15px">%s</p>
            %s
            <hr>
            <p style="color:#888;font-size:12px">
                Alert key: %s — suppressed for %dh after this notice.
                Inspect raw events: <a href="%s">audit log</a>.
            </p>
            """.formatted(headlineHtml, bodyHtml, esc(key), ALERT_COOLDOWN.toHours(), adminUrl("/admin/audit"));
        emailService.send(new EmailMessage(alertEmail, "Halilov admin",
            "[Halilov] Security anomaly: " + subject, html));
        log.warn("[anomaly] fired alert: {}", key);
    }

    // ---------- HTML helpers ----------

    private String describeUser(User u) {
        StringBuilder s = new StringBuilder();
        s.append("Yes, role=<b>").append(u.getRole()).append("</b>");
        if (!u.isEnabled()) s.append(", <b>DISABLED</b>");
        Instant lockedUntil = u.getLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(Instant.now())) {
            long mins = Math.max(1, Duration.between(Instant.now(), lockedUntil).toMinutes() + 1);
            s.append(", currently <b>LOCKED</b> for ~").append(mins).append(" more min");
        } else {
            s.append(", unlocked");
        }
        if (u.isTotpEnabled()) s.append(", 2FA on");
        return s.toString();
    }

    private static String kv(String k, String v) {
        return "<tr><td style=\"padding:4px 12px 4px 0;color:#666\">" + k + "</td><td>" + v + "</td></tr>";
    }

    private static String facts(String... rows) {
        return "<table style=\"border-collapse:collapse\">" + String.join("", rows) + "</table>";
    }

    private static <T> String listSection(String title, List<T> rows, java.util.function.Function<T, String> render) {
        if (rows.isEmpty()) return "";
        StringBuilder s = new StringBuilder();
        s.append("<h4 style=\"margin-bottom:4px\">").append(title).append("</h4><ul style=\"margin-top:4px\">");
        for (T r : rows) s.append("<li>").append(render.apply(r)).append("</li>");
        s.append("</ul>");
        return s.toString();
    }

    private static String playbook(String... items) {
        StringBuilder s = new StringBuilder();
        s.append("<h4 style=\"margin-bottom:4px\">What to do</h4><ul style=\"margin-top:4px\">");
        for (String it : items) s.append("<li>").append(it).append("</li>");
        s.append("</ul>");
        return s.toString();
    }

    private String adminUrl(String path) {
        return siteBaseUrl.isBlank() ? path : siteBaseUrl + path;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
