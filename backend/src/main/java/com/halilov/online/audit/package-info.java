/**
 * Append-only audit log for security-sensitive and order-state events.
 *
 * <p>Every call to {@link com.halilov.online.audit.AuditService#record}
 * (and its overloads) writes one row to {@code audit_log} in a
 * {@code REQUIRES_NEW} transaction, so audit entries persist even when
 * the surrounding business transaction rolls back. Action codes are
 * captured in {@link com.halilov.online.audit.AuditAction} —
 * {@code USER_LOGIN}, {@code USER_LOGIN_FAILED},
 * {@code ORDER_PLACED}, {@code ORDER_STATUS_CHANGED}, etc.
 *
 * <p>The {@code audit_log} table is the source of truth for the admin
 * Audit page and the {@link com.halilov.online.security.AnomalyMonitor}
 * detector. An hourly job snapshots it off-host to Cloudflare R2 for
 * tamper-evidence.
 *
 * <p>Read paths use a Spring Data {@code Specification} so the admin UI
 * can filter by actor, action, entity, and time range without exposing
 * raw SQL.
 */
package com.halilov.online.audit;
