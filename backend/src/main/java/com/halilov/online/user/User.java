package com.halilov.online.user;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * The {@code users} table — single identity for both customers and
 * admins.
 *
 * <p>Beyond profile fields, this row owns a fair amount of security
 * state: bcrypt {@code password_hash}, {@code enabled} (admin can
 * disable an account), {@code failed_login_count} +
 * {@code locked_until} (5-strike auto-lockout in {@link com.halilov.online.auth.AuthService}),
 * {@code force_logout_at} (the cut-off the auth filter compares JWT
 * {@code iat} against), {@code totp_secret} + {@code totp_enabled}
 * (admin 2FA), and marketing-consent columns
 * ({@code marketing_opt_in}, {@code marketing_consent_at},
 * {@code unsubscribe_token}).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.CUSTOMER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn = false;

    @Column(name = "marketing_consent_at")
    private Instant marketingConsentAt;

    @Column(name = "unsubscribe_token", unique = true)
    private String unsubscribeToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "force_logout_at")
    private Instant forceLogoutAt;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "totp_secret")
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled = false;

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isMarketingOptIn() { return marketingOptIn; }
    public void setMarketingOptIn(boolean marketingOptIn) { this.marketingOptIn = marketingOptIn; }
    public Instant getMarketingConsentAt() { return marketingConsentAt; }
    public void setMarketingConsentAt(Instant marketingConsentAt) { this.marketingConsentAt = marketingConsentAt; }
    public String getUnsubscribeToken() { return unsubscribeToken; }
    public void setUnsubscribeToken(String unsubscribeToken) { this.unsubscribeToken = unsubscribeToken; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getForceLogoutAt() { return forceLogoutAt; }
    public void setForceLogoutAt(Instant forceLogoutAt) { this.forceLogoutAt = forceLogoutAt; }
    public int getFailedLoginCount() { return failedLoginCount; }
    public void setFailedLoginCount(int failedLoginCount) { this.failedLoginCount = failedLoginCount; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
    public String getTotpSecret() { return totpSecret; }
    public void setTotpSecret(String totpSecret) { this.totpSecret = totpSecret; }
    public boolean isTotpEnabled() { return totpEnabled; }
    public void setTotpEnabled(boolean totpEnabled) { this.totpEnabled = totpEnabled; }
}
