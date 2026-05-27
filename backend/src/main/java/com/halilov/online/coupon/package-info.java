/**
 * Discount codes.
 *
 * <p>Two coupon shapes via {@link com.halilov.online.coupon.CouponType}:
 * {@code PERCENT} (whole-percent off subtotal) and {@code FIXED} (flat
 * agorot off). Optional minimum subtotal, optional max-usage counter,
 * optional active-from / active-until window.
 *
 * <p>{@link com.halilov.online.coupon.CouponService#resolveForOrder}
 * is the single entry point used by the checkout flow — it validates,
 * computes the discount in agorot, and returns an {@code AppliedCoupon}
 * snapshot. {@code incrementUsage} fires on PENDING → PAID;
 * {@code decrementUsage} reverses it on cancel/refund.
 *
 * <p>Customer-facing validation is exposed via
 * {@code POST /api/coupons/validate} so the cart can preview the discount
 * without committing an order.
 */
package com.halilov.online.coupon;
