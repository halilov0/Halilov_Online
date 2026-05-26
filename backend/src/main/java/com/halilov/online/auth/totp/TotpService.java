package com.halilov.online.auth.totp;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.halilov.online.user.User;
import com.halilov.online.user.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Wraps the third-party TOTP library so the rest of the app talks in our
 * vocabulary (User / plaintext code). Handles secret generation, code
 * verification with a ±1 step window (30s), QR-code provisioning URIs, and
 * recovery-code minting/hashing.
 */
@Service
public class TotpService {

    private static final int RECOVERY_CODE_COUNT = 8;
    private static final int RECOVERY_CODE_BYTES = 6; // base32-ish → ~10 char codes
    private static final SecureRandom RNG = new SecureRandom();

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6);
    private final CodeVerifier codeVerifier;

    private final UserRepository users;
    private final TotpRecoveryCodeRepository recoveryRepo;
    private final String issuer;

    public TotpService(UserRepository users,
                       TotpRecoveryCodeRepository recoveryRepo,
                       @Value("${app.totp.issuer:Halilov Admin}") String issuer) {
        this.users = users;
        this.recoveryRepo = recoveryRepo;
        this.issuer = issuer;
        DefaultCodeVerifier v = new DefaultCodeVerifier(codeGenerator, timeProvider);
        // ±1 step (30s) tolerance — handles minor clock drift between the
        // user's phone and the server.
        v.setAllowedTimePeriodDiscrepancy(1);
        this.codeVerifier = v;
    }

    /** Generate a fresh base32 secret. Not persisted by this call. */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    /** Build the otpauth:// URI for QR display. */
    public String provisioningUri(String secret, String accountLabel) {
        // Manually compose otpauth URI — keeps us off QrData/QrGenerator which
        // require a separate ZXing dep. The admin UI renders the QR with a
        // pure-JS lib.
        String enc = java.net.URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        String acct = java.net.URLEncoder.encode(accountLabel, StandardCharsets.UTF_8);
        return "otpauth://totp/" + enc + ":" + acct
            + "?secret=" + secret
            + "&issuer=" + enc
            + "&algorithm=SHA1&digits=6&period=30";
    }

    /** Constant-time-ish TOTP check. */
    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null) return false;
        return codeVerifier.isValidCode(secret, code.trim());
    }

    /** Generate N fresh recovery codes, persist their hashes, and return the
     *  plaintext codes for the user to write down. Wipes any prior codes for
     *  the same user — re-enrollment invalidates the old set. */
    public List<String> regenerateRecoveryCodes(Long userId) {
        recoveryRepo.deleteAllByUserId(userId);
        List<String> plaintexts = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = randomRecoveryCode();
            plaintexts.add(code);
            TotpRecoveryCode row = new TotpRecoveryCode();
            row.setUserId(userId);
            row.setCodeHash(sha256(code));
            recoveryRepo.save(row);
        }
        return plaintexts;
    }

    /** Burn a recovery code if it matches. Returns true on hit. */
    public boolean consumeRecoveryCode(Long userId, String code) {
        if (code == null || code.isBlank()) return false;
        String normalized = code.trim().toUpperCase().replace("-", "");
        String hash = sha256(normalized);
        List<TotpRecoveryCode> active = recoveryRepo.findByUserIdAndUsedAtIsNullOrderById(userId);
        for (TotpRecoveryCode row : active) {
            if (constantTimeEquals(row.getCodeHash(), hash)) {
                row.setUsedAt(java.time.Instant.now());
                recoveryRepo.save(row);
                return true;
            }
        }
        return false;
    }

    public void disable(User user) {
        user.setTotpSecret(null);
        user.setTotpEnabled(false);
        users.save(user);
        recoveryRepo.deleteAllByUserId(user.getId());
    }

    private static String randomRecoveryCode() {
        byte[] buf = new byte[RECOVERY_CODE_BYTES];
        RNG.nextBytes(buf);
        // base32-ish: split into two 5-char groups separated by a dash for
        // readability when the user copies them down.
        String b32 = base32(buf).toUpperCase();
        return b32.substring(0, 5) + "-" + b32.substring(5, 10);
    }

    private static String base32(byte[] data) {
        // Tiny inline base32 (RFC 4648 alphabet) — keeps us off commons-codec.
        final char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                sb.append(alphabet[(buffer >> bits) & 31]);
            }
        }
        if (bits > 0) {
            sb.append(alphabet[(buffer << (5 - bits)) & 31]);
        }
        return sb.toString();
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}
