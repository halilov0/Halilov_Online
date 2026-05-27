/**
 * Cross-cutting security: JWT, per-request authentication filter, and
 * login-anomaly detection.
 *
 * <ul>
 *   <li>{@link com.halilov.online.security.JwtService} — HS256 sign and
 *       parse. Secret comes from {@code app.jwt.secret} (≥32 chars in
 *       prod). Tokens include {@code sub} (email), {@code role}, and
 *       an {@code iat} truncated to whole seconds so equality with
 *       {@code users.force_logout_at} survives the DB round-trip.</li>
 *   <li>{@link com.halilov.online.security.JwtAuthenticationFilter} —
 *       runs before
 *       {@code UsernamePasswordAuthenticationFilter}. Parses the bearer
 *       token, then <em>re-reads the user row</em> on every request so
 *       admin disables and force-logouts take effect within one
 *       request, not when the JWT eventually expires.</li>
 *   <li>{@link com.halilov.online.security.AppUserDetailsService} —
 *       Spring Security plumbing for password-encoder lookups (used by
 *       the password reset flow).</li>
 *   <li>{@link com.halilov.online.security.AnomalyMonitor} — scheduled
 *       scan of {@code audit_log} for failed-login bursts (per-account
 *       and per-IP), sign-up abuse, and cancel spikes. Per-(kind,
 *       scope) dedupe is held in {@code anomaly_alerts} with a 1-hour
 *       cooldown.</li>
 * </ul>
 *
 * <p>The {@code AnomalyMonitor} per-IP signal is the one that actually
 * catches credential stuffing — per-account thresholds rarely fire on
 * fake emails / already-locked accounts.
 */
package com.halilov.online.security;
