package com.halilov.online.auth.totp;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.halilov.online.audit.AuditAction;
import com.halilov.online.audit.AuditService;
import com.halilov.online.user.User;
import com.halilov.online.user.UserRepository;

import java.util.List;

/**
 * Self-service 2FA enrollment for the logged-in admin. Three steps:
 *   1. POST /enroll         → returns a fresh secret, QR URI, and one-shot
 *                             recovery codes. Secret is staged on the user
 *                             but {@code totp_enabled} stays false.
 *   2. POST /confirm {code} → if the code verifies, flips enabled = true.
 *   3. POST /disable {code} → wipe secret + recovery rows.
 *
 * The endpoints sit under /api/me/totp so SecurityConfig's authenticated()
 * rule covers them. We additionally hard-check ADMIN role inside each
 * handler since customers have no reason to enroll today.
 */
@RestController
@RequestMapping("/api/me/totp")
public class TotpController {

    private final UserRepository users;
    private final TotpService totp;
    private final AuditService audit;

    public TotpController(UserRepository users, TotpService totp, AuditService audit) {
        this.users = users;
        this.totp = totp;
        this.audit = audit;
    }

    public record EnrollResponse(String secret, String qrUri, List<String> recoveryCodes) {}
    public record StatusResponse(boolean enrolled) {}
    public record ConfirmRequest(@NotBlank String code) {}
    public record DisableRequest(@NotBlank String code) {}

    @GetMapping("/status")
    public StatusResponse status(Authentication auth) {
        User u = requireAdmin(auth);
        return new StatusResponse(u.isTotpEnabled());
    }

    @PostMapping("/enroll")
    @Transactional
    public EnrollResponse enroll(Authentication auth) {
        User u = requireAdmin(auth);
        String secret = totp.generateSecret();
        u.setTotpSecret(secret);
        u.setTotpEnabled(false); // not active until /confirm
        users.save(u);
        List<String> recovery = totp.regenerateRecoveryCodes(u.getId());
        String uri = totp.provisioningUri(secret, u.getEmail());
        return new EnrollResponse(secret, uri, recovery);
    }

    @PostMapping("/confirm")
    @Transactional
    public StatusResponse confirm(Authentication auth, @Valid @RequestBody ConfirmRequest req) {
        User u = requireAdmin(auth);
        if (u.getTotpSecret() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no enrollment in progress");
        }
        if (!totp.verifyCode(u.getTotpSecret(), req.code())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "קוד שגוי");
        }
        u.setTotpEnabled(true);
        users.save(u);
        audit.recordAs(u.getId(), u.getEmail(), u.getRole().name(),
            AuditAction.USER_TOTP_ENROLLED, "user", u.getId(),
            "2FA הופעל: " + u.getEmail(), null);
        return new StatusResponse(true);
    }

    @PostMapping("/disable")
    @Transactional
    public StatusResponse disable(Authentication auth, @Valid @RequestBody DisableRequest req) {
        User u = requireAdmin(auth);
        if (!u.isTotpEnabled() || u.getTotpSecret() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "2fa not enabled");
        }
        // Require the current code to disable — otherwise an attacker who
        // momentarily steals the JWT could turn 2FA off without the phone.
        if (!totp.verifyCode(u.getTotpSecret(), req.code())
            && !totp.consumeRecoveryCode(u.getId(), req.code())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "קוד שגוי");
        }
        totp.disable(u);
        audit.recordAs(u.getId(), u.getEmail(), u.getRole().name(),
            AuditAction.USER_TOTP_DISABLED, "user", u.getId(),
            "2FA הושבת: " + u.getEmail(), null);
        return new StatusResponse(false);
    }

    private User requireAdmin(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no session");
        }
        User u = users.findByEmail(auth.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no user"));
        if (u.getRole() != com.halilov.online.user.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admin only");
        }
        return u;
    }
}
