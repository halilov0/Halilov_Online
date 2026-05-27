package com.halilov.online.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.halilov.online.audit.AuditAction;
import com.halilov.online.audit.AuditService;
import com.halilov.online.auth.totp.TotpChallengeStore;
import com.halilov.online.auth.totp.TotpService;
import com.halilov.online.auth.totp.TrustedIpService;
import com.halilov.online.security.JwtService;
import com.halilov.online.user.Role;
import com.halilov.online.user.User;
import com.halilov.online.user.UserRepository;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Core auth flows: register, login (with two-stage admin 2FA), and
 * {@code /me}. JWT issuance is delegated to
 * {@link com.halilov.online.security.JwtService}.
 *
 * <p>Login is the interesting part. Customer logins are single-stage —
 * a successful password check returns a JWT immediately. Admin logins
 * with TOTP enrolled return a JWT only when the request IP is trusted
 * (see {@link com.halilov.online.auth.totp.TrustedIpService}); otherwise
 * the response is a {@link LoginOutcome.ChallengeResponse}-bearing
 * outcome and the client must follow up with
 * {@link #completeTotpLogin}.
 *
 * <p>Per-account lockout (5 wrong passwords ⇒ 15 minutes locked) is
 * enforced here. The lockout check runs <em>before</em> bcrypt so a
 * hammering attacker burns no CPU while the account is cooling down.
 * Lockout state is intentionally surfaced to the user — the real user
 * already knows the account exists, and an attacker who's been
 * incrementing the counter likewise knows it exists.
 */
@Service
public class AuthService {

    /** Failed-attempt threshold per account before lockout kicks in. */
    private static final int MAX_FAILED_LOGINS = 5;
    /** How long an account stays locked after the threshold is crossed. */
    private static final java.time.Duration LOCKOUT_DURATION = java.time.Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService audit;
    private final TotpService totp;
    private final TotpChallengeStore challenges;
    private final TrustedIpService trustedIps;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuditService audit,
                       TotpService totp,
                       TotpChallengeStore challenges,
                       TrustedIpService trustedIps) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.audit = audit;
        this.totp = totp;
        this.challenges = challenges;
        this.trustedIps = trustedIps;
    }

    @Transactional
    public AuthDtos.TokenResponse register(AuthDtos.RegisterRequest req) {
        String email = req.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullName(req.fullName().trim());
        user.setPhone(req.phone());
        user.setRole(Role.CUSTOMER);
        boolean optIn = Boolean.TRUE.equals(req.marketingOptIn());
        user.setMarketingOptIn(optIn);
        if (optIn) {
            user.setMarketingConsentAt(Instant.now());
            user.setUnsubscribeToken(UUID.randomUUID().toString().replace("-", ""));
        }
        userRepository.save(user);
        audit.recordAs(user.getId(), user.getEmail(), user.getRole().name(),
            AuditAction.USER_REGISTER, "user", user.getId(),
            "משתמש נרשם: " + user.getEmail(), null);
        return toToken(user);
    }

    /** Result of step 1 (email + password). Exactly one of {@code token} or
     *  {@code challenge} is populated. ADMIN with 2FA enabled from a
     *  non-trusted IP gets a challenge and must call {@link #completeTotpLogin}. */
    public record LoginOutcome(AuthDtos.TokenResponse token, AuthDtos.ChallengeResponse challenge) {
        public static LoginOutcome ofToken(AuthDtos.TokenResponse t) { return new LoginOutcome(t, null); }
        public static LoginOutcome ofChallenge(String c) {
            return new LoginOutcome(null, new AuthDtos.ChallengeResponse(true, c));
        }
    }

    @Transactional
    public LoginOutcome login(AuthDtos.LoginRequest req, String clientIp) {
        String email = req.email().toLowerCase().trim();
        User user = userRepository.findByEmail(email).orElse(null);

        // Unknown email — same generic 401, no counter to bump.
        if (user == null) {
            audit.recordAs(null, email, null,
                AuditAction.USER_LOGIN_FAILED, "user", null,
                "ניסיון התחברות נכשל: " + email, null);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad credentials");
        }

        // Lockout check runs before bcrypt so a hammering attacker burns no
        // CPU while the account is cooling down. Surfacing the lockout in
        // the response is intentional UX — the real user already knows the
        // account exists, and an attacker who's been incrementing the
        // counter likewise knows it exists.
        Instant now = Instant.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            long minutes = Math.max(1, java.time.Duration.between(now, user.getLockedUntil()).toMinutes() + 1);
            audit.recordAs(user.getId(), user.getEmail(), user.getRole().name(),
                AuditAction.USER_LOGIN_FAILED, "user", user.getId(),
                "ניסיון התחברות לחשבון נעול: " + email, null);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "החשבון ננעל זמנית עקב ניסיונות חוזרים. נסו שוב בעוד " + minutes + " דקות.");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            int failed = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(failed);
            if (failed >= MAX_FAILED_LOGINS) {
                user.setLockedUntil(now.plus(LOCKOUT_DURATION));
                user.setFailedLoginCount(0);
                audit.recordAs(user.getId(), user.getEmail(), user.getRole().name(),
                    AuditAction.USER_LOGIN_LOCKED, "user", user.getId(),
                    "חשבון נעול אוטומטית לאחר " + MAX_FAILED_LOGINS + " ניסיונות כושלים: " + email, null);
            } else {
                audit.recordAs(user.getId(), user.getEmail(), user.getRole().name(),
                    AuditAction.USER_LOGIN_FAILED, "user", user.getId(),
                    "ניסיון התחברות נכשל: " + email + " (" + failed + "/" + MAX_FAILED_LOGINS + ")", null);
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad credentials");
        }

        if (!user.isEnabled()) {
            audit.recordAs(user.getId(), user.getEmail(), user.getRole().name(),
                AuditAction.USER_LOGIN_FAILED, "user", user.getId(),
                "ניסיון התחברות לחשבון מושבת: " + email, null);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "account disabled");
        }

        // Password OK — clear any partial failure state. We don't yet count
        // this as a successful login because 2FA may still block it.
        if (user.getFailedLoginCount() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
        }

        // 2FA gate: only ADMIN with TOTP enrolled, only when the login is
        // from an IP not on the trusted list. CUSTOMER never sees 2FA.
        if (user.getRole() == Role.ADMIN && user.isTotpEnabled() && !trustedIps.isTrusted(clientIp)) {
            String challenge = challenges.issue(user.getId());
            audit.recordAs(user.getId(), user.getEmail(), user.getRole().name(),
                AuditAction.USER_LOGIN_2FA_CHALLENGE, "user", user.getId(),
                "אתגר 2FA נדרש מ-IP " + (clientIp == null ? "?" : clientIp), null);
            return LoginOutcome.ofChallenge(challenge);
        }

        audit.recordAs(user.getId(), user.getEmail(), user.getRole().name(),
            AuditAction.USER_LOGIN, "user", user.getId(),
            "התחברות: " + user.getEmail(), null);
        return LoginOutcome.ofToken(toToken(user));
    }

    /** Step 2 of the admin 2FA flow. Consumes the challenge, validates a
     *  TOTP code or one-shot recovery code, and issues the JWT. */
    @Transactional
    public AuthDtos.TokenResponse completeTotpLogin(String challenge, String code) {
        Long userId = challenges.consume(challenge);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "challenge expired");
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no user"));
        if (!user.isEnabled() || !user.isTotpEnabled() || user.getTotpSecret() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "2fa unavailable");
        }
        boolean ok = totp.verifyCode(user.getTotpSecret(), code)
            || totp.consumeRecoveryCode(user.getId(), code);
        if (!ok) {
            audit.recordAs(user.getId(), user.getEmail(), user.getRole().name(),
                AuditAction.USER_LOGIN_2FA_FAILED, "user", user.getId(),
                "קוד 2FA שגוי: " + user.getEmail(), null);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "קוד 2FA שגוי");
        }
        audit.recordAs(user.getId(), user.getEmail(), user.getRole().name(),
            AuditAction.USER_LOGIN, "user", user.getId(),
            "התחברות (אומת 2FA): " + user.getEmail(), null);
        return toToken(user);
    }

    public AuthDtos.MeResponse me(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no session"));
        return new AuthDtos.MeResponse(
            user.getId(), user.getEmail(), user.getFullName(),
            user.getPhone(), user.getRole().name(), user.isMarketingOptIn()
        );
    }

    private AuthDtos.TokenResponse toToken(User user) {
        String token = jwtService.issue(user);
        return new AuthDtos.TokenResponse(token, user.getEmail(), user.getRole().name(), user.getFullName());
    }
}
