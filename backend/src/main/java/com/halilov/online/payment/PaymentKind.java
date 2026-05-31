package com.halilov.online.payment;

/** A money movement against an order. {@code REFUND} support lands in Phase 6. */
public enum PaymentKind {
    CHARGE, REFUND
}
