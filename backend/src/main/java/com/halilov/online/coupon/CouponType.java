package com.halilov.online.coupon;

/**
 * How {@link Coupon#getValue()} is interpreted: whole-percent off the
 * (post-shipping) subtotal, or a flat agorot discount.
 */
public enum CouponType {
    PERCENT,
    FIXED
}
