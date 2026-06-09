package com.halilov.online.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.halilov.online.auth.totp.TrustedDeviceService;
import com.halilov.online.common.ClientIp;

import java.time.Duration;

/**
 * Public authentication surface under {@code /api/auth}.
 *
 * <p>Hosts register, login (single-stage for customers, two-stage for
 * admins with 2FA), {@code /me}, and the forgot-password / reset flow.
 * All listed routes are explicitly {@code permitAll} in
 * {@link com.halilov.online.config.SecurityConfig} — they predate the
 * JWT being available on the client.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService resetService;

    public AuthController(AuthService authService, PasswordResetService resetService) {
        this.authService = authService;
        this.resetService = resetService;
    }

    @PostMapping("/register")
    public AuthDtos.TokenResponse register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
        return authService.register(req);
    }

    /**
     * Returns either a {@link AuthDtos.TokenResponse} (login complete) or a
     * {@link AuthDtos.ChallengeResponse} (2FA required) — the client checks
     * for {@code requires2FA} on the response to decide which path to take.
     */
    @PostMapping("/login")
    public Object login(@Valid @RequestBody AuthDtos.LoginRequest req, HttpServletRequest http) {
        String deviceToken = readCookie(http, TrustedDeviceService.COOKIE);
        AuthService.LoginOutcome outcome = authService.login(req, ClientIp.resolve(http), deviceToken);
        return outcome.challenge() != null ? outcome.challenge() : outcome.token();
    }

    @PostMapping("/login/totp")
    public AuthDtos.TokenResponse loginTotp(@Valid @RequestBody AuthDtos.TotpLoginRequest req,
                                            HttpServletResponse resp) {
        AuthService.TotpResult result = authService.completeTotpLogin(
            req.challenge(), req.code(), Boolean.TRUE.equals(req.trustDevice()));
        if (result.deviceCookieToken() != null) {
            resp.addHeader(HttpHeaders.SET_COOKIE, buildDeviceCookie(result.deviceCookieToken()));
        }
        return result.token();
    }

    private static String readCookie(HttpServletRequest req, String name) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    /** Build the trusted-device cookie: HttpOnly + Secure + SameSite=Strict,
     *  scoped to the auth endpoints, living for {@link TrustedDeviceService#TTL_DAYS}. */
    private static String buildDeviceCookie(String token) {
        return ResponseCookie.from(TrustedDeviceService.COOKIE, token)
            .httpOnly(true)
            .secure(true)
            .sameSite("Strict")
            .path("/api/auth")
            .maxAge(Duration.ofDays(TrustedDeviceService.TTL_DAYS))
            .build()
            .toString();
    }

    @GetMapping("/me")
    public AuthDtos.MeResponse me(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no session");
        }
        return authService.me(auth.getName());
    }

    /**
     * Forgot-password from the login page. Always returns 204, regardless of
     * whether the email exists, to prevent enumeration.
     */
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        resetService.requestForEmail(req.email());
    }

    @GetMapping("/password-reset/{token}")
    public PasswordResetService.TokenInfo validateReset(@PathVariable String token) {
        return resetService.validate(token);
    }

    @PostMapping("/password-reset/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeReset(@PathVariable String token,
                              @Valid @RequestBody CompleteResetRequest req) {
        resetService.complete(token, req.newPassword());
    }

    public record ForgotPasswordRequest(@Email @NotBlank String email) {}
    public record CompleteResetRequest(@NotBlank @Size(min = 8, max = 100) String newPassword) {}
}
