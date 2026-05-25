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

    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest req) {
        String email = req.email().toLowerCase().trim();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            audit.recordAs(user == null ? null : user.getId(), email,
                user == null ? null : user.getRole().name(),
                AuditAction.USER_LOGIN_FAILED, "user", user == null ? null : user.getId(),
                "ניסיון התחברות נכשל: " + email, null);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad credentials");
        }
        if (!user.isEnabled()) {
            audit.recordAs(user.getId(), user.getEmail(), user.getRole().name(),
                AuditAction.USER_LOGIN_FAILED, "user", user.getId(),
                "ניסיון התחברות לחשבון מושבת: " + email, null);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "account disabled");
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
