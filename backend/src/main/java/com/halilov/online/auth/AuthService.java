package com.halilov.online.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.halilov.online.audit.AuditAction;
import com.halilov.online.audit.AuditService;
import com.halilov.online.security.JwtService;
import com.halilov.online.user.Role;
import com.halilov.online.user.User;
import com.halilov.online.user.UserRepository;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

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

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuditService audit) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.audit = audit;
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

    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest req) {
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

        // Successful login — clear any partial failure state.
        if (user.getFailedLoginCount() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
        }
        audit.recordAs(user.getId(), user.getEmail(), user.getRole().name(),
            AuditAction.USER_LOGIN, "user", user.getId(),
            "התחברות: " + user.getEmail(), null);
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
