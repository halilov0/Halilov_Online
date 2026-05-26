package com.halilov.online.auth.totp;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived 2FA challenge tokens, in-memory. Step 1 (password) issues a
 * challenge; step 2 (TOTP code) consumes it. State is intentionally
 * non-persistent — a backend restart simply forces the user to retype the
 * password, which is fine for a 1-VM deploy.
 */
@Component
public class TotpChallengeStore {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int TOKEN_BYTES = 24;
    private static final SecureRandom RNG = new SecureRandom();

    private final Map<String, Pending> open = new ConcurrentHashMap<>();

    public String issue(Long userId) {
        purgeExpired();
        String token = randomToken();
        open.put(token, new Pending(userId, Instant.now().plus(TTL)));
        return token;
    }

    /** Returns the userId if the token is live; null otherwise. Consumes
     *  the entry on success — challenges are single-use. */
    public Long consume(String token) {
        if (token == null) return null;
        Pending p = open.remove(token);
        if (p == null) return null;
        if (p.expiresAt.isBefore(Instant.now())) return null;
        return p.userId;
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        open.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(now));
    }

    private static String randomToken() {
        byte[] buf = new byte[TOKEN_BYTES];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private record Pending(Long userId, Instant expiresAt) {}
}
