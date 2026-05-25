package com.halilov.online.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.halilov.online.audit.AuditAction;
import com.halilov.online.audit.AuditService;
import com.halilov.online.notification.EmailMessage;
import com.halilov.online.notification.EmailService;
import com.halilov.online.user.User;
import com.halilov.online.user.UserRepository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TOKEN_TTL = Duration.ofHours(1);
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RNG = new SecureRandom();

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditService audit;
    private final String siteBaseUrl;

    public PasswordResetService(UserRepository users,
                                PasswordResetTokenRepository tokens,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService,
                                AuditService audit,
                                @Value("${app.email.siteBaseUrl:}") String siteBaseUrl) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.audit = audit;
        this.siteBaseUrl = siteBaseUrl;
    }

    /**
     * Customer-initiated "forgot password" from the login page. Always returns
     * silently — we never confirm or deny that the email maps to an account,
     * to avoid leaking which addresses are registered.
     */
    @Transactional
    public void requestForEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) return;
        String email = rawEmail.toLowerCase().trim();
        users.findByEmail(email).ifPresent(u -> issueAndEmail(u, false));
    }

    /**
     * Admin-initiated reset for a specific user. Throws 404 if the user is
     * gone so the admin UI can show a real error.
     */
    @Transactional
    public void requestForUser(Long userId) {
        User u = users.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));
        issueAndEmail(u, true);
    }

    /**
     * Pre-flight check used by the reset page on the shop SPA to decide
     * whether to show the form or a "this link is invalid / expired" message.
     * Returns the user's email so the page can confirm "you're resetting the
     * password for X" without us echoing it back from the URL.
     */
    @Transactional(readOnly = true)
    public TokenInfo validate(String token) {
        PasswordResetToken row = loadRedeemable(token);
        User u = users.findById(row.getUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "user removed"));
        return new TokenInfo(u.getEmail(), row.getExpiresAt());
    }

    /**
     * Burn the token, set the new password, force-logout existing sessions.
     */
    @Transactional
    public void complete(String token, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password too short");
        }
        PasswordResetToken row = loadRedeemable(token);
        User u = users.findById(row.getUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE, "user removed"));
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        // Invalidate every JWT issued before this reset — the old password
        // shouldn't survive a successful change.
        u.setForceLogoutAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        row.setUsedAt(Instant.now());
        audit.recordAs(u.getId(), u.getEmail(), u.getRole().name(),
            AuditAction.PASSWORD_RESET_COMPLETED, "user", u.getId(),
            "סיסמה אופסה דרך אימייל: " + u.getEmail(), null);
    }

    private void issueAndEmail(User u, boolean triggeredByAdmin) {
        String token = randomToken();
        PasswordResetToken row = new PasswordResetToken();
        row.setToken(token);
        row.setUserId(u.getId());
        row.setExpiresAt(Instant.now().plus(TOKEN_TTL));
        tokens.save(row);

        String base = (siteBaseUrl == null || siteBaseUrl.isBlank())
            ? "" : siteBaseUrl.trim().replaceAll("/+$", "");
        String resetUrl = base + "/password-reset/" + token;

        try {
            emailService.send(new EmailMessage(
                u.getEmail(),
                u.getFullName(),
                PasswordResetEmailBuilder.subject(),
                PasswordResetEmailBuilder.html(u.getFullName(), resetUrl, triggeredByAdmin),
                List.of()
            ));
        } catch (Exception e) {
            // EmailService already persists to outbox + retries; this catch
            // is belt-and-braces so we don't 500 the user-facing endpoint.
            log.warn("password reset email send failed for {}: {}", u.getEmail(), e.toString());
        }

        audit.recordAs(triggeredByAdmin ? null : u.getId(),
            u.getEmail(),
            triggeredByAdmin ? null : u.getRole().name(),
            AuditAction.PASSWORD_RESET_REQUESTED, "user", u.getId(),
            triggeredByAdmin
                ? "מנהל יזם איפוס סיסמה: " + u.getEmail()
                : "משתמש ביקש איפוס סיסמה: " + u.getEmail(),
            null);
    }

    private PasswordResetToken loadRedeemable(String token) {
        Optional<PasswordResetToken> opt = (token == null || token.isBlank())
            ? Optional.empty() : tokens.findByToken(token);
        PasswordResetToken row = opt.orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "token not found"));
        if (row.getUsedAt() != null) {
            throw new ResponseStatusException(HttpStatus.GONE, "token already used");
        }
        if (row.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "token expired");
        }
        return row;
    }

    private static String randomToken() {
        byte[] buf = new byte[TOKEN_BYTES];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    public record TokenInfo(String email, Instant expiresAt) {}
}
