package com.halilov.online.notification;

/**
 * Delivery state of an {@link EmailOutbox} row. {@code PENDING} is
 * picked up by the retry sweep until it transitions to {@code SENT}
 * (success) or {@code FAILED} (max attempts reached).
 */
public enum EmailOutboxStatus {
    PENDING,
    SENT,
    FAILED
}
