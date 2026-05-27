package com.halilov.online.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.halilov.online.user.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * HS256 JWT issue + parse. The secret comes from
 * {@code app.jwt.secret} and must be at least 32 characters in any
 * environment that's reachable from the public internet.
 *
 * <p>Tokens carry {@code sub} (email), {@code uid}, {@code role}, and
 * timestamps. {@code iat} is truncated to whole seconds because the
 * auth filter compares it against {@code users.force_logout_at} — a
 * sub-second mismatch from the DB round-trip would otherwise let a
 * just-issued token slip past a freshly-stamped force-logout.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.expirationMinutes}") long expirationMinutes
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String issue(User user) {
        // Round down to whole seconds because JWT `iat` is unix-seconds
        // precision — keeping millis would let a force_logout_at written in
        // the same second slip through the > comparison in the auth filter.
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant exp = now.plusSeconds(expirationMinutes * 60);
        return Jwts.builder()
            .subject(user.getEmail())
            .claim("uid", user.getId())
            .claim("role", user.getRole().name())
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .signWith(key)
            .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
