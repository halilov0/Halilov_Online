package com.halilov.online.security;

import com.halilov.online.audit.AuditAction;
import com.halilov.online.audit.AuditLogRepository;
import com.halilov.online.notification.EmailMessage;
import com.halilov.online.notification.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Periodic scan of {@code audit_log} for known attack signatures, fires an
 * email to {@code SECURITY_ALERT_EMAIL} (falls back to {@code EMAIL_ADMIN_BCC})
 * when any threshold is exceeded.
 * <p>
 * Watched signatures:
 * <ul>
 *   <li>Failed logins per account &ge; {@value #FAILED_LOGIN_THRESHOLD} / hour
 *       — password attack against a specific user.</li>
 *   <li>Successful registrations per IP &ge; {@value #REGISTRATION_THRESHOLD}
 *       / hour — automated sign-up abuse.</li>
 *   <li>Cancellations &ge; {@value #CANCELLATION_THRESHOLD} in 5 minutes —
 *       potential exploitation of the cancel/refund flow.</li>
 * </ul>
 * Per-(kind, scope) dedupe via {@code anomaly_alerts} table: once an alert
 * fires, the same scope is suppressed for {@link #ALERT_COOLDOWN}. Sustained
 * attacks re-alert at the cooldown boundary instead of hammering inbox.
 */
@Component
public class AnomalyMonitor {

    private static final Logger log = LoggerFactory.getLogger(AnomalyMonitor.class);

    static final long FAILED_LOGIN_THRESHOLD = 10;
    static final long REGISTRATION_THRESHOLD = 5;
    static final long CANCELLATION_THRESHOLD = 50;

    private static final Duration FAILED_LOGIN_WINDOW = Duration.ofHours(1);
    private static final Duration REGISTRATION_WINDOW = Duration.ofHours(1);
    private static final Duration CANCELLATION_WINDOW = Duration.ofMinutes(5);
    private static final Duration ALERT_COOLDOWN = Duration.ofHours(1);

    private final AuditLogRepository audit;
    private final AnomalyAlertRepository state;
    private final EmailService emailService;
    private final String alertEmail;
    private final String siteBaseUrl;

    public AnomalyMonitor(AuditLogRepository audit,
                          AnomalyAlertRepository state,
                          EmailService emailService,
                          @Value("${app.security.alertEmail:${app.email.adminBcc:}}") String alertEmail,
                          @Value("${app.email.siteBaseUrl:}") String siteBaseUrl) {
        this.audit = audit;
        this.state = state;
        this.emailService = emailService;
        this.alertEmail = alertEmail == null ? "" : alertEmail.trim();
        this.siteBaseUrl = siteBaseUrl == null ? "" : siteBaseUrl.trim();
    }

    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void scan() {
        if (alertEmail.isBlank()) {
            return;
        }
        try { scanFailedLogins();      } catch (Exception e) { log.warn("[anomaly] failed-login scan errored: {}", e.toString()); }
        try { scanRegistrationAbuse(); } catch (Exception e) { log.warn("[anomaly] registration scan errored: {}", e.toString()); }
        try { scanCancellationSpike(); } catch (Exception e) { log.warn("[anomaly] cancellation scan errored: {}", e.toString()); }
    }

    private void scanFailedLogins() {
        Instant since = Instant.now().minus(FAILED_LOGIN_WINDOW);
        for (var row : audit.groupByActorEmailSince(AuditAction.USER_LOGIN_FAILED, since, FAILED_LOGIN_THRESHOLD)) {
            String key = "FAILED_LOGINS:" + row.getScope();
            if (!claim(key)) continue;
            sendAlert("Failed logins on " + row.getScope(),
                "Account <b>" + esc(row.getScope()) + "</b> had <b>" + row.getCnt()
                    + "</b> failed login attempts in the last hour (threshold: "
                    + FAILED_LOGIN_THRESHOLD + ").",
                key);
        }
    }

    private void scanRegistrationAbuse() {
        Instant since = Instant.now().minus(REGISTRATION_WINDOW);
        for (var row : audit.groupByActorIpSince(AuditAction.USER_REGISTER, since, REGISTRATION_THRESHOLD)) {
            String key = "REGISTER_ABUSE:" + row.getScope();
            if (!claim(key)) continue;
            sendAlert("Sign-up abuse from " + row.getScope(),
                "IP <b>" + esc(row.getScope()) + "</b> registered <b>" + row.getCnt()
                    + "</b> accounts in the last hour (threshold: "
                    + REGISTRATION_THRESHOLD + ").",
                key);
        }
    }

    private void scanCancellationSpike() {
        Instant since = Instant.now().minus(CANCELLATION_WINDOW);
        long cnt = audit.countActionSince(AuditAction.ORDER_CANCELLED, since);
        if (cnt < CANCELLATION_THRESHOLD) return;
        String key = "CANCEL_SPIKE";
        if (!claim(key)) return;
        sendAlert("Cancellation spike",
            "<b>" + cnt + "</b> orders were cancelled in the last 5 minutes (threshold: "
                + CANCELLATION_THRESHOLD + "). Investigate immediately.",
            key);
    }

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

    private void sendAlert(String subject, String bodyFragment, String key) {
        String auditUrl = siteBaseUrl.isBlank() ? "/admin/audit" : siteBaseUrl + "/admin/audit";
        String html = """
            <p>Anomaly detected on Halilov Online:</p>
            <p>%s</p>
            <p>Inspect: <a href="%s">audit log</a></p>
            <hr>
            <p style="color:#888;font-size:12px">Alert key: %s — suppressed for %dh after this notice.</p>
            """.formatted(bodyFragment, esc(auditUrl), esc(key), ALERT_COOLDOWN.toHours());
        emailService.send(new EmailMessage(alertEmail, "Halilov admin",
            "[Halilov] Security anomaly: " + subject, html));
        log.warn("[anomaly] fired alert: {}", key);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
