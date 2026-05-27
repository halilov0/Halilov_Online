/**
 * Spring configuration and bootstrap.
 *
 * <ul>
 *   <li>{@link com.halilov.online.config.SecurityConfig} — wires the
 *       {@link com.halilov.online.security.JwtAuthenticationFilter} into
 *       the filter chain, configures CORS (explicit allowlist via
 *       {@code app.cors.allowedOrigins}), and declares the public vs
 *       authenticated request matchers. CSRF is disabled because the
 *       app is stateless (JWT in {@code Authorization} header).</li>
 *   <li>{@link com.halilov.online.config.DataSeeder} — runs once at
 *       startup to ensure the initial admin user exists (from
 *       {@code INIT_ADMIN_*} env vars). Idempotent; safe to keep
 *       enabled in prod.</li>
 * </ul>
 *
 * <p>The seeded admin credentials default to {@code admin@halilov.local}
 * / {@code admin123!} — fine for local dev, must be overridden before
 * any environment that's reachable.
 */
package com.halilov.online.config;
