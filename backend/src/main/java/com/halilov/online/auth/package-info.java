/**
 * User-facing authentication surface: register, login, current-user
 * lookup, and password reset.
 *
 * <p>Login is two-stage for admins with 2FA enrolled (see
 * {@link com.halilov.online.auth.totp}); step one returns either a JWT
 * (customer or trusted-IP admin) or a short-lived TOTP challenge. The
 * challenge is consumed by {@code POST /api/auth/login/totp}.
 *
 * <p>Password reset is enumeration-safe — the {@code forgot-password}
 * endpoint always returns 204 regardless of whether the email exists.
 * Reset tokens are single-use and time-boxed
 * ({@link com.halilov.online.auth.PasswordResetToken}).
 *
 * <p>Failed-login lockout lives in
 * {@link com.halilov.online.auth.AuthService}: 5 wrong passwords arms a
 * 15-minute lock. Lockout state is intentionally surfaced to the real
 * user — an attacker bumping the counter already knows the account
 * exists.
 *
 * <p>JWT signing/parsing is delegated to
 * {@link com.halilov.online.security.JwtService}.
 */
package com.halilov.online.auth;
