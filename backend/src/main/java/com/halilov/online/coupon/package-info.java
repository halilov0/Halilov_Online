/**
 * Discount codes.
 *
 * <p>A coupon carries any mix of independent benefits (a "super coupon"):
 * {@code percentOff} (whole-percent off subtotal), {@code fixedOffAgorot}
 * (flat agorot off), and {@code freeShipping}. {@code maxDiscountAgorot}
 * caps the combined subtotal discount. Eligibility gates: optional minimum
 * subtotal, optional global max-usage counter, optional once-per-customer
 * rule, and an optional {@code activeFrom}/{@code expiresAt} window.
 *
 * <p>{@link com.halilov.online.coupon.CouponService#resolveForOrder}
 * is the single entry point used by the checkout flow — it validates,
 * computes the discount in agorot, and returns an {@code AppliedCoupon}
 * snapshot (discount + free-shipping + once-per-customer flags). The
 * once-per-customer rule is enforced by
 * {@link com.halilov.online.order.OrderService} (it owns the buyer
 * identity). {@code incrementUsage} fires on PENDING → PAID;
 * {@code decrementUsage} reverses it on cancel/refund.
 *
 * <p>Customer-facing validation is exposed via
 * {@code POST /api/coupons/validate} so the cart can preview the discount
 * without committing an order.
 */
package com.halilov.online.coupon;
