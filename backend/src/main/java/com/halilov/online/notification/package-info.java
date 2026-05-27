/**
 * Transactional email: outbox pattern + builder per message type.
 *
 * <p>Every send goes through
 * {@link com.halilov.online.notification.EmailService#send}, which
 * persists the row to {@code email_outbox} <em>before</em> attempting
 * delivery. A {@code @Scheduled} {@code retryPending()} drains stuck
 * rows on a 30-minute cadence, retrying up to ~48h before giving up.
 * Survives Brevo 429s, transient outages, and VM restarts.
 *
 * <p>{@link com.halilov.online.notification.EmailTransport} is the SPI;
 * two implementations:
 * <ul>
 *   <li>{@link com.halilov.online.notification.BrevoEmailTransport} —
 *       calls Brevo's transactional API (300/day on the free tier).</li>
 *   <li>{@link com.halilov.online.notification.LogEmailTransport} —
 *       logs the message instead of sending it. Default in dev.</li>
 * </ul>
 *
 * <p>Builders ({@code OrderEmailBuilder},
 * {@code PasswordResetEmailBuilder}, {@code BackInStockEmailBuilder})
 * own the Hebrew copy and HTML structure for each transactional message.
 * Keep them framework-free so they're trivially unit-testable.
 *
 * <p>{@link com.halilov.online.notification.StockNotificationService}
 * lives here because back-in-stock notifications are the same outbox
 * mechanism — customers register their email against an out-of-stock
 * product and get a one-shot email when stock returns.
 */
package com.halilov.online.notification;
