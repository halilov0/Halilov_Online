package com.halilov.online.auth.totp;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One-shot 2FA recovery code. Plaintext is shown to the admin exactly
 * once at enrollment; only the SHA-256 hex lands in this table. Each
 * row is consumed at most once ({@code used_at}). Losing every active
 * recovery code is the trigger for the manual SSH+SQL recovery
 * documented in user memory.
 */
@Entity
@Table(name = "totp_recovery_codes")
public class TotpRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** SHA-256 hex of the plaintext code. Plaintext is shown to the user
     *  exactly once at enrollment and never persisted. */
    @Column(name = "code_hash", nullable = false, length = 128)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
