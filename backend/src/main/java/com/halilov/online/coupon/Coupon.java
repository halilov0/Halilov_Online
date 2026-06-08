package com.halilov.online.coupon;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * The {@code coupons} table.
 *
 * <p>A coupon carries any mix of independent benefits, all optional but
 * at least one required (enforced by {@code coupons_has_benefit_chk}):
 * <ul>
 *   <li>{@code percentOff} — whole percent off the subtotal (1-100)</li>
 *   <li>{@code fixedOffAgorot} — flat agorot off the subtotal</li>
 *   <li>{@code freeShipping} — waives the delivery fee</li>
 * </ul>
 * {@code maxDiscountAgorot} caps the combined subtotal discount (handy to
 * bound a percent coupon on a big cart). Eligibility gates:
 * {@code minSubtotalAgorot}, {@code maxUses} (global), {@code oncePerCustomer}
 * (enforced at order creation by {@link com.halilov.online.order.OrderService}),
 * and the {@code activeFrom}/{@code expiresAt} window. {@code usedCount} is
 * mutated by {@code OrderService} in lockstep with order state, never by the
 * admin UI.
 */
@Entity
@Table(name = "coupons")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    /** Whole percent (1-100) off the subtotal, or null. */
    @Column(name = "percent_off")
    private Integer percentOff;

    /** Flat agorot off the subtotal, or null. */
    @Column(name = "fixed_off_agorot")
    private Integer fixedOffAgorot;

    /** Waives the delivery fee. */
    @Column(name = "free_shipping", nullable = false)
    private boolean freeShipping = false;

    /** Caps the combined subtotal discount (agorot), or null for no cap. */
    @Column(name = "max_discount_agorot")
    private Integer maxDiscountAgorot;

    @Column(name = "min_subtotal_agorot", nullable = false)
    private int minSubtotalAgorot = 0;

    @Column(name = "max_uses")
    private Integer maxUses;

    /** Each customer may redeem the code once (by account or guest email). */
    @Column(name = "once_per_customer", nullable = false)
    private boolean oncePerCustomer = false;

    @Column(name = "used_count", nullable = false)
    private int usedCount = 0;

    /** Scheduled start — the code is inert before this instant, or null. */
    @Column(name = "active_from")
    private Instant activeFrom;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Integer getPercentOff() { return percentOff; }
    public void setPercentOff(Integer percentOff) { this.percentOff = percentOff; }
    public Integer getFixedOffAgorot() { return fixedOffAgorot; }
    public void setFixedOffAgorot(Integer fixedOffAgorot) { this.fixedOffAgorot = fixedOffAgorot; }
    public boolean isFreeShipping() { return freeShipping; }
    public void setFreeShipping(boolean freeShipping) { this.freeShipping = freeShipping; }
    public Integer getMaxDiscountAgorot() { return maxDiscountAgorot; }
    public void setMaxDiscountAgorot(Integer maxDiscountAgorot) { this.maxDiscountAgorot = maxDiscountAgorot; }
    public int getMinSubtotalAgorot() { return minSubtotalAgorot; }
    public void setMinSubtotalAgorot(int minSubtotalAgorot) { this.minSubtotalAgorot = minSubtotalAgorot; }
    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }
    public boolean isOncePerCustomer() { return oncePerCustomer; }
    public void setOncePerCustomer(boolean oncePerCustomer) { this.oncePerCustomer = oncePerCustomer; }
    public int getUsedCount() { return usedCount; }
    public void setUsedCount(int usedCount) { this.usedCount = usedCount; }
    public Instant getActiveFrom() { return activeFrom; }
    public void setActiveFrom(Instant activeFrom) { this.activeFrom = activeFrom; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
}
