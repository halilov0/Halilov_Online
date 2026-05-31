package com.halilov.online.payment;

/**
 * Green Invoice receipt issuance state, tracked independently of the charge so
 * a GI outage never fails a paid order: the charge commits {@code PAID} and the
 * receipt is retried from {@code PENDING}/{@code FAILED} by {@link ReceiptRetryJob}.
 * {@code null} (not in this enum) = no issuance attempted yet.
 */
public enum GiStatus {
    PENDING, ISSUED, FAILED
}
