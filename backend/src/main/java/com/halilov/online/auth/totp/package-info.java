/**
 * Admin-only 2FA. Time-based one-time passwords (RFC 6238) via the
 * {@code dev.samstevens.totp} library.
 *
 * <p>Flow:
 * <ol>
 *   <li>Enrollment ({@link com.halilov.online.auth.totp.TotpController}):
 *       admin requests setup, server generates a secret + provisioning
 *       URI; admin scans the QR in any authenticator app and confirms
 *       with a code before the secret is persisted to
 *       {@code users.totp_secret}.</li>
 *   <li>Recovery codes ({@link com.halilov.online.auth.totp.TotpRecoveryCode}):
 *       one-shot codes issued at enrollment, stored hashed, consumed on
 *       use. Persisted across enrollments so a lost phone can be
 *       recovered without DB surgery (but losing both means the SSH+SQL
 *       path documented in user memory).</li>
 *   <li>Login challenge: when an ADMIN logs in from an IP not on the
 *       trusted list, step one returns a challenge instead of a JWT.
 *       The challenge is stored in
 *       {@link com.halilov.online.auth.totp.TotpChallengeStore} (in-memory,
 *       short TTL) and consumed by {@code POST /api/auth/login/totp}.</li>
 * </ol>
 *
 * <p>{@link com.halilov.online.auth.totp.TrustedIpService} parses
 * {@code app.admin.trustedIps} (plain IPs and CIDR blocks, IPv4 + IPv6)
 * and answers {@code isTrusted(remoteAddr)}. /64 is the right granularity
 * for IPv6 since SLAAC rotates the host portion.
 */
package com.halilov.online.auth.totp;
