/**
 * Small, domain-free utilities reused across packages.
 *
 * <ul>
 *   <li>{@link com.halilov.online.common.Csv} — CSV writer that prepends
 *       the UTF-8 BOM so Excel opens Hebrew-bearing files correctly
 *       without prompting for encoding.</li>
 *   <li>{@link com.halilov.online.common.InMemoryThrottle} — single-VM
 *       rate-limit helper for endpoints that are too low-stakes for a
 *       full bucket implementation (e.g. password-reset request).</li>
 *   <li>{@link com.halilov.online.common.HealthController} — wraps the
 *       Actuator health check at a stable {@code /api/ping} path so
 *       Docker / nginx don't need to know about Actuator's URL layout.</li>
 * </ul>
 *
 * <p>Anything that grows a real interface or a second implementation
 * graduates out of {@code common} into its own package.
 */
package com.halilov.online.common;
