# Backend — `com.halilov.online`

Spring Boot 3.3 service (Java 21) backing the Halilov Online shop and
admin SPAs. Stateless REST under `/api/**`, JWT auth, Postgres + Flyway,
JPA persistence.

See [ARCHITECTURE.md](../ARCHITECTURE.md) for the full system diagram.
This README is the entry point for *working in the backend module*.

## Stack

| Concern        | Choice                                              |
|----------------|-----------------------------------------------------|
| Runtime        | Java 21 (Temurin), Spring Boot 3.3.4                |
| HTTP           | `spring-boot-starter-web` (Tomcat)                  |
| Auth           | `spring-boot-starter-security` + `jjwt` (HS256)     |
| 2FA            | `dev.samstevens.totp` (RFC 6238 TOTP)               |
| Persistence    | `spring-boot-starter-data-jpa` + Postgres           |
| Migrations     | Flyway (`flyway-core` + `flyway-database-postgresql`) |
| Validation     | `spring-boot-starter-validation` (Jakarta)          |
| HTML sanitize  | `jsoup` (marketing email body)                       |
| Media (cloud)  | AWS SDK v2 `s3` against Cloudflare R2                |
| Build          | Maven (`pom.xml`)                                    |

## Run locally

```
# Postgres + the app, defaults from infra/compose
docker compose -f ../infra/docker-compose.yml up --build backend

# Or run with maven against an already-running Postgres
DB_URL=jdbc:postgresql://localhost:5432/halilov \
DB_USER=halilov DB_PASSWORD=halilov_dev \
JWT_SECRET=dev-only-change-me-dev-only-change-me-32+chars-min \
./mvnw spring-boot:run
```

Health check: `GET http://localhost:8080/actuator/health`.

Flyway runs at startup. Schema is in
[src/main/resources/db/migration](src/main/resources/db/migration) —
**never edit a shipped migration**; add `V{n+1}__*.sql`.

## Package map

All production code lives under `com.halilov.online.*`. Each subpackage
ships a `package-info.java` with a one-paragraph rundown of what's in it
and why. Quick map:

| Package        | Purpose                                                              |
|----------------|----------------------------------------------------------------------|
| `auth`         | Register, login, `/me`, forgot-password. Issues JWT.                 |
| `auth.totp`    | Admin TOTP enroll/verify, recovery codes, trusted-IP bypass.         |
| `security`     | JWT service, per-request filter, login-anomaly monitor.              |
| `config`       | Spring Security wiring, dev data seeder.                             |
| `user`         | User entity, saved addresses, account self-service, admin user CRUD. |
| `catalog`      | Products, categories, sitemap, public + admin catalog APIs.          |
| `cart`         | Server-backed cart lines for authenticated users.                    |
| `favorites`    | Server-backed wishlist (per-user set of product ids) for authenticated users. |
| `coupon`       | Discount codes (PERCENT / FIXED) and usage tracking.                 |
| `order`        | Order lifecycle, addresses, refunds, CSV export, delivery pricing.   |
| `payment`      | Mock payment provider (zero PCI scope).                              |
| `notification` | Email outbox + builders (transactional). Brevo or stdout transport.  |
| `marketing`    | Opt-in list, campaign send, unsubscribe link.                        |
| `media`        | Image storage interface (local disk + Cloudflare R2).                |
| `audit`        | Append-only audit log (security-sensitive events).                   |
| `metrics`      | Admin dashboard counters (today's orders, revenue, etc.).            |
| `places`       | IL postal places autocomplete proxy.                                 |
| `common`       | Tiny utilities (CSV writer with BOM, throttle, health endpoint).     |

## Configuration

All runtime configuration is in
[src/main/resources/application.yml](src/main/resources/application.yml).
Most values are pulled from env vars with sensible dev defaults. Notable
ones:

| Env var                 | Purpose                                                       |
|-------------------------|---------------------------------------------------------------|
| `JWT_SECRET`            | HS256 signing key. **Must** be ≥32 chars in any real env.     |
| `DB_URL/USER/PASSWORD`  | Postgres connection.                                          |
| `ADMIN_TRUSTED_IPS`     | Comma-separated IPs/CIDR that bypass admin 2FA challenge.     |
| `SECURITY_ALERT_EMAIL`  | Where anomaly alerts are sent (falls back to `EMAIL_ADMIN_BCC`). |
| `EMAIL_PROVIDER`        | `brevo` for prod SMTP-API, `log` for stdout in dev.           |
| `BREVO_API_KEY`         | Brevo API key when `EMAIL_PROVIDER=brevo`.                    |
| `MEDIA_STORAGE`         | `local` (disk) or `r2` (Cloudflare).                          |
| `R2_*`                  | R2 endpoint, keys, bucket, public base URL.                   |
| `PAYMENT_PROVIDER`      | Currently only `mock` is wired up.                            |
| `SITE_BASE_URL`         | Used in outgoing email links (e.g. order-paid receipt).       |
| `INIT_ADMIN_*`          | First-boot seeded admin user. Overwrite before going public.  |

## Conventions

- **Money in agorot.** All prices are stored as integer agorot (NIS×100).
  No floats anywhere.
- **VAT = 0.** Halilov is registered as a עוסק פטור (VAT-exempt). New
  orders persist `vat_agorot = 0`. The column stays for historical
  pre-exemption rows.
- **Hebrew error reasons reach the SPA** via `response.body.message`
  because [application.yml](src/main/resources/application.yml) sets
  `server.error.include-message: always`. Without that flag Spring
  strips the reason from `ResponseStatusException` and the client only
  sees the bare HTTP status. `include-binding-errors` stays off —
  bean-validation messages aren't curated for users. If you ever
  disable `include-message`, restore status→copy maps in the SPAs
  before shipping. The shop's `authStore.login` already maps 401/403
  to fixed Hebrew copy as a stable override.
- **Audit-everything.** Every security-sensitive or order-state action
  goes through `AuditService.record*(...)`. The write runs in a
  `REQUIRES_NEW` transaction so it commits even if the caller rolls
  back.
- **Repositories return Optional.** Service code unwraps with
  `orElseThrow(new ResponseStatusException(404, ...))` — don't leak
  `EmptyResultDataAccessException` to controllers.
- **DTOs are records.** Group request/response shapes in
  `XxxDtos.java` per package.

## Testing

```
./mvnw test
```

`spring-boot-starter-test` + `spring-security-test` are wired up. There's
no enforced coverage gate — pragmatic, integration-style tests for the
flows that hurt when they break (auth, order state, cart sync).
