package com.halilov.online.payment;

/**
 * Lifecycle of one {@link Payment} row.
 * {@code INITIATED} = gateway order created, payer not yet captured.
 * {@code PAID} = capture confirmed server-side (the row carries the capture id).
 * {@code FAILED} = capture declined/errored. {@code REFUNDED} = Phase 6.
 */
public enum PaymentStatus {
    INITIATED, PAID, FAILED, REFUNDED
}
