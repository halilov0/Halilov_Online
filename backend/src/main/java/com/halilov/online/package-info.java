/**
 * Root package of the Halilov Online backend.
 *
 * <p>The application is a stateless Spring Boot 3.3 service (Java 21)
 * fronted by nginx and Cloudflare. All HTTP traffic enters under
 * {@code /api/**}; auth is JWT (HS256). Persistence is Postgres with
 * Flyway-managed schema and Spring Data JPA.
 *
 * <p>See {@code docs/ARCHITECTURE.md} for the system diagram and
 * {@code backend/README.md} for build/run instructions. Each subpackage
 * carries its own {@code package-info.java} with package-local notes.
 *
 * <p>Conventions worth knowing before reading code in this tree:
 * <ul>
 *   <li>Money is stored as integer agorot (NIS &times; 100). No floats.</li>
 *   <li>The business is a registered עוסק פטור — VAT is always 0 on
 *       new orders, but {@code vat_agorot} is kept for historical rows.</li>
 *   <li>Security-sensitive flows write to {@code audit_log} via
 *       {@link com.halilov.online.audit.AuditService} in a
 *       {@code REQUIRES_NEW} transaction so audit entries commit even
 *       if the surrounding tx rolls back.</li>
 *   <li>{@code ResponseStatusException} reasons are stripped by Spring
 *       in the response body — clients see only the status text. Hebrew
 *       error copy lives in the SPA.</li>
 * </ul>
 */
package com.halilov.online;
