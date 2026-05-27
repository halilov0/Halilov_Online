package com.halilov.online.coupon;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * The {@code coupons} table.
 *
 * <p>{@code value} is interpreted by {@link CouponType}: whole
 * percent for {@code PERCENT}, integer agorot for {@code FIXED}.
 * Optional {@code min_subtotal_agorot}, {@code max_uses}, and
 * {@code active_from}/{@code active_until} gate eligibility.
 * {@code usage_count} is mutated by
 * {@link com.halilov.online.order.OrderService} in lockstep with
 * order state transitions, never by the admin UI.
 */
@Entity
@Table(name = "coupons")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(nullable = false)
    private int value;

    @Column(name = "min_subtotal_agorot", nullable = false)
    private int minSubtotalAgorot = 0;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "used_count", nullable = false)
    private int usedCount = 0;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public CouponType getType() { return type; }
    public void setType(CouponType type) { this.type = type; }
    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }
    public int getMinSubtotalAgorot() { return minSubtotalAgorot; }
    public void setMinSubtotalAgorot(int minSubtotalAgorot) { this.minSubtotalAgorot = minSubtotalAgorot; }
    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }
    public int getUsedCount() { return usedCount; }
    public void setUsedCount(int usedCount) { this.usedCount = usedCount; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
}
