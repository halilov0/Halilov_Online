package com.halilov.online.order;

/**
 * Order state machine. Forward path:
 * {@code PENDING → PAID → FULFILLED → SHIPPED → DELIVERED}.
 * {@code CANCELLED} and {@code REFUNDED} are terminal off-ramps.
 *
 * <p>The only transition that mutates stock is
 * {@code PENDING → PAID}; cancels and refunds reverse the decrement.
 * Customer self-cancel is permitted through {@code FULFILLED} — after
 * {@code SHIPPED} the parcel is with the courier and the customer
 * must contact support.
 */
public enum OrderStatus {
    PENDING, PAID, FULFILLED, SHIPPED, DELIVERED, CANCELLED, REFUNDED
}
