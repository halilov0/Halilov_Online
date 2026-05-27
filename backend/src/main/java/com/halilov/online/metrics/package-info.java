/**
 * Admin dashboard counters.
 *
 * <p>Read-only aggregates over orders, products, and users —
 * today's orders, today's revenue, low-stock count, recent signups,
 * etc. Surfaces at {@code /api/admin/metrics/*}.
 *
 * <p>Intentionally not Prometheus-shaped: the goal is "what should I
 * look at right now" for the operator, not long-term observability.
 * Anything truly time-series belongs in a dedicated metrics pipeline,
 * not here.
 */
package com.halilov.online.metrics;
