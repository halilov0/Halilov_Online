package com.halilov.online.auth.totp;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.halilov.online.user.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * "Remember this device" for admin 2FA. After a successful TOTP login the
 * client may be issued a long-lived signed token (stored as the
 * {@code hm_device} cookie). On a later login from the same browser the token
 * lets the admin skip the 2FA challenge for {@link #TTL_DAYS} days.
 *
 * <p>Security properties:
 * <ul>
 *   <li><b>Separate signing key</b> — derived from the JWT secret with a
 *       distinct suffix, so a device token can never be replayed as a session
 *       JWT (and vice-versa); the two verifiers reject each other's tokens.</li>
 *   <li><b>User-bound</b> — the {@code uid} claim must match the account that
 *       is logging in, so one admin's device cookie can't waive another's 2FA.</li>
 *   <li><b>Revocable</b> — a token issued before the user's
 *       {@code force_logout_at} is rejected, so a password reset / lost-phone
 *       force-logout drops trusted devices along with live sessions.</li>
 *   <li><b>Expiry</b> — enforced by the JWT parser ({@code exp} claim).</li>
 * </ul>
 *
 * <p>Stateless by design: no device registry table. Global revocation rides
 * the existing {@code force_logout_at} lever rather than per-device rows.
 */
@Service
public class TrustedDeviceService {

    /** Cookie name carrying the trusted-device token. */
    public static final String COOKIE = "hm_device";

    public static final long TTL_DAYS = 30;

    private static final String TYP = "device";

    private final SecretKey key;

    public TrustedDeviceService(@Value("${app.jwt.secret}") String secret) {
        // Distinct key from the session JWT so the two token types are not
        // interchangeable even though they share the base secret.
        this.key = Keys.hmacShaKeyFor((secret + "::trusted-device").getBytes(StandardCharsets.UTF_8));
    }

    /** Mint a fresh device token for {@code user}, valid for {@link #TTL_DAYS}. */
    public String issue(User user) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return Jwts.builder()
            .claim("uid", user.getId())
            .claim("typ", TYP)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(Duration.ofDays(TTL_DAYS))))
            .signWith(key)
            .compact();
    }

    /**
     * True when {@code token} is a valid, non-expired device token bound to
     * {@code user} and issued after any force-logout cutoff. Any parse/verify
     * failure (bad signature, expired, wrong type) returns false — fail closed.
     */
    public boolean isValidFor(User user, String token) {
        if (user == null || token == null || token.isBlank()) return false;
        try {
            Claims c = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
            if (!TYP.equals(c.get("typ", String.class))) return false;
            Number uid = c.get("uid", Number.class);
            if (uid == null || uid.longValue() != user.getId()) return false;
            Instant forceLogoutAt = user.getForceLogoutAt();
            if (forceLogoutAt != null) {
                Date iat = c.getIssuedAt();
                if (iat == null || iat.toInstant().isBefore(forceLogoutAt)) return false;
            }
            return true; // exp already enforced by the parser
        } catch (Exception e) {
            return false;
        }
    }
}
