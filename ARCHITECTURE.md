# Halilov Online — Architecture

End-to-end overview of how the system is built and how the parts talk to
each other. Read this first; package- and module-level docs go deeper.

## 1. System shape

```
                ┌───────────────┐
   visitor ───▶ │  Cloudflare   │  DNS, TLS (Full/Strict, 15-yr Origin Cert),
                │  (proxied)    │  WAF, caching, rate-limit rules
                └──────┬────────┘
                       │  HTTPS (Origin Cert)
                ┌──────▼────────┐
                │     nginx     │  one origin, three static frontends + /api proxy
                │  (Docker)     │
                └──────┬────────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
   /  (shop)     /admin (SPA)     /api/**
        │              │              │
   shop SPA      admin SPA            ▼
   (Vite+React)  (Vite+React)   ┌──────────────┐
                                 │ Spring Boot  │
                                 │ (backend)    │
                                 └──────┬───────┘
                                        │
                          ┌─────────────┼───────────────┐
                          │             │               │
                    ┌─────▼──────┐ ┌────▼──────┐ ┌──────▼──────┐
                    │ Postgres   │ │ Brevo     │ │ Cloudflare R2│
                    │ (Flyway)   │ │ (SMTP API)│ │ (S3-compat)  │
                    └────────────┘ └───────────┘ └──────────────┘
```

- **Edge**: Cloudflare proxies `halilov.co.il`. TLS terminates at CF; the
  origin presents a CF Origin Certificate (15-yr). HSTS deliberately off
  for now.
- **Origin**: A single Oracle Cloud VM (`158.180.49.247`) runs Docker
  with the frontends + backend + Postgres. nginx is the only port 443
  listener.
- **Frontends**: Two Vite + React + TypeScript SPAs (shop and admin),
  built to static bundles and served by nginx. They share an origin with
  the API, so no CORS is needed in steady state.
- **Backend**: Spring Boot 3.3 (Java 21) — REST under `/api/**`, stateless
  (JWT), Flyway-managed schema, JPA persistence.
- **Data plane**: Postgres in Docker, with a daily snapshot of the audit
  log to Cloudflare R2 (offsite tamper-evidence).

## 2. Repo layout

```
backend/           Spring Boot 3.3 service — see backend/README.md
shop/              Customer SPA (Vite + React + TS)
admin/             Admin SPA (Vite + React + TS)
docs/              Documentation (this file, requirements, legal PDFs)
infra/             Compose files, nginx config, deploy scripts
assets/            Static design assets (logos, etc.)
seo/               SEO progress trackers (URL inspection state)
keys/              Local-only key material (gitignored)
```

The three runnable units (`backend`, `shop`, `admin`) each have their own
`Dockerfile` and README. `infra/` glues them together.

## 3. Domain modules (backend)

The backend is structured as Spring `@Component`-style modules under one
package root (`com.halilov.online`). Each subpackage carries a
`package-info.java` with package-level Javadoc. Quick map:

| Package         | Responsibility                                                              |
|-----------------|------------------------------------------------------------------------------|
| `auth`          | Register / login / `/me` / forgot-password. Issues JWT.                      |
| `auth.totp`     | Admin TOTP (RFC 6238) 2FA, recovery codes, trusted-IP bypass.                |
| `security`      | JWT signing + per-request auth filter + login-anomaly monitor.               |
| `config`        | Spring Security config, seed data.                                           |
| `user`          | `User`, roles, saved addresses, account self-service, admin user CRUD.       |
| `catalog`       | Products, categories, sitemap, public + admin catalog APIs.                  |
| `cart`          | Server-backed cart lines for signed-in users (`cart_lines` table).           |
| `coupon`        | Discount codes (PERCENT / FIXED), usage counters, validation.                |
| `order`         | Order lifecycle, addresses, delivery method/pricing, refunds, CSV export.    |
| `payment`       | Mock payment provider (no real PSP wired up yet — zero PCI scope).           |
| `notification`  | Transactional email outbox + builders (order paid, reset, back-in-stock).    |
| `marketing`     | Opt-in subscriber list, campaign send, one-click unsubscribe.                |
| `media`         | Local + R2 image storage, on-upload image processing.                        |
| `audit`         | Append-only `audit_log` for security-relevant + business events.             |
| `metrics`       | Lightweight admin dashboard counters (today's orders, revenue, etc.).        |
| `places`        | IL postal places autocomplete proxy (city pick-list).                        |
| `common`        | Tiny utilities (CSV writer with BOM, in-memory throttle, health endpoint).   |

### 3.1 Authentication & authorization

- **Token format**: HS256 JWT, signed by `JwtService` with the secret from
  `app.jwt.secret`. Claims: `sub` = email, `role` = `CUSTOMER` | `ADMIN`,
  `iat` truncated to whole seconds (so equality with `force_logout_at`
  works after DB round-trip).
- **Per-request filter**: `JwtAuthenticationFilter` parses the token,
  then re-reads `users` to honor admin-disable + force-logout *within
  one request* instead of waiting for the JWT to expire. One extra row
  read per authenticated request — acceptable at current traffic.
- **Admin 2FA**: ADMIN role + TOTP enrolled + request IP not in
  `ADMIN_TRUSTED_IPS` ⇒ login returns a `challenge` instead of a token.
  Client posts the TOTP/recovery code to `/api/auth/login/totp` to
  complete. `ADMIN_TRUSTED_IPS` accepts plain IPs and CIDR (handy for
  stable IPv6 /64 blocks).
- **CORS**: explicit allowlist via `app.cors.allowedOrigins`. In prod the
  SPA and API share an origin so it's effectively empty — kept as
  defense-in-depth.
- **Rate limiting / lockout**: per-account login lockout after 5 failed
  attempts (15-minute window). The `AnomalyMonitor` watches failed
  logins, sign-up bursts, and cancel spikes and inserts into
  `anomaly_alerts` for the admin Security page.

### 3.2 Order & payment flow

```
client                backend                  payment provider
  │   POST /orders     │                            │
  │ ─────────────────▶ │  create Order(PENDING),    │
  │                    │  mint guest_token if guest │
  │                    │                            │
  │   POST /orders/{n}/pay
  │ ─────────────────▶ │  return mock checkout URL  │
  │                    │ ─────────────────────────▶ │
  │   redirect ◀────────┴────────────────────────── │
  │                                                  │
  │   POST /payments/mock/{n}/complete               │
  │ ──────────────────────────────────────────────▶ │
  │                    │ ◀──── completion (mock) ── │
  │                    │  Order → PAID, decrement   │
  │                    │  stock, send email, bump   │
  │                    │  coupon usage              │
```

- **PENDING → PAID** is the *only* transition that decrements stock and
  bumps coupon usage. Cancels/refunds reverse both.
- **Guest checkout**: an order with no `user_id` gets a random
  `guest_token` returned once at create time. Anonymous reads + the
  payment-complete callback require the token via `X-Guest-Token`
  header (or `?t=` on share links).
- **Share token**: a registered user can mint a `share_token` for an
  order they own (idempotent — same token returned on repeat). Lets the
  buyer email the invoice link to an accountant without sharing
  credentials.
- **Ownership errors**: when a logged-in user requests an order they
  don't own, we return **403 (Forbidden)**, not 404. Distinguishes
  "wrong account" from "wrong order number" in the SPA without leaking
  the owner's identity.

### 3.3 Cart

Signed-in users have server-backed cart lines (`cart_lines` table).
The client treats the server as the source of truth and uses:

- **Debounced PUT** of full cart state from active tab.
- **`BroadcastChannel` + `storage` events** to mirror changes across tabs
  on the same device.
- **`visibilitychange` refetch** to pick up cross-device changes when a
  tab returns to foreground.

Guests keep the cart entirely in `localStorage`. On login the local cart
is merged into the server cart.

### 3.4 Money & taxes

- All prices are stored as **integer agorot** (NIS × 100). No floats.
- Halilov Online is registered as a **עוסק פטור** — VAT-exempt. New
  orders persist `vat_agorot = 0`. The column survives for historical
  records pre-exemption.

### 3.5 Media

`MediaStorage` is an interface with two implementations:

- `LocalFileMediaStorage` — disk under `media/`, served via
  `MediaWebConfig`. Used in dev and as the prod default.
- `R2MediaStorage` — Cloudflare R2 via the AWS S3 SDK. Used when
  `app.media.r2.*` is configured.

`ImageProcessor` runs single-image transforms on upload (resize,
re-encode). No multi-image composition.

### 3.6 Audit & anomaly

- `AuditService.record*(...)` writes append-only rows to `audit_log`.
  All security-sensitive flows (login, password reset, admin actions,
  order state changes) go through it.
- A `REQUIRES_NEW` transaction wraps the write so it commits even if the
  caller rolls back — we want failed/blocked actions audited too.
- `AnomalyMonitor` is a `@Scheduled` job that scans recent audit rows
  and inserts to `anomaly_alerts` when thresholds are crossed
  (failed-login bursts per-IP, sign-up abuse, cancel spikes). Alerts
  lead with the playbook step, not the raw event.
- The audit log is snapshotted hourly to R2 for off-host
  tamper-evidence.

## 4. Frontends

Two Vite + React + TypeScript SPAs, shared structure:

- `api.ts` — `fetch` wrapper. Injects `Authorization: Bearer ...` (from
  `localStorage`) and `X-Guest-Token` (from `sessionStorage`, scoped to
  the order number in the URL). Throws `ApiError` with Hebrew copy on
  HTTP errors.
- `auth/authStore.ts` — Zustand store, token + user state, login/logout.
- `App.tsx` — Router, top-level `useEffect` that hydrates the user
  (`fetchMe`) when a token is present.
- `pages/` — one component per route.
- `components/` — shared UI.

Shop-only:

- `cart/cartStore.ts` — local cart + debounced sync to backend.
- `favorites/favoritesStore.ts` — localStorage favorites list.
- `delivery/deliveryConfig.ts` — *backend-served* delivery config
  cached on the client (single source of truth lives in `delivery_method`
  table; the client just mirrors it via `/api/delivery/config`).
- `lib/invoicePdf.ts` — client-side PDF render of the invoice.

Admin-only:

- `components/RequireAdmin.tsx` — gate. Redirects to login if no token,
  to `/` if token but role ≠ ADMIN.

### 4.1 Auth hydration race

When the app boots with a token in `localStorage`, the store has the
token *immediately* but `user` is null until `fetchMe()` resolves.
Protected routes must therefore gate on **`!token`**, not `!user`,
otherwise they bounce to login on every F5.

### 4.2 Hebrew-first, RTL

All UI copy is Hebrew. `index.html` sets `dir="rtl"`. Most error
messages come from the backend; `extractErrorMessage` in `api.ts` falls
back to generic Hebrew strings for 429 / 503 / unparseable bodies.

## 5. Database

- Postgres in Docker. Schema is **Flyway-managed** —
  `backend/src/main/resources/db/migration/V*.sql`. Never edit a shipped
  migration; add `V{n+1}__*.sql`.
- When narrowing a CHECK constraint or removing an enum value, add a
  data-migration script in the *same* PR as the code change (so the
  migration fails fast if there's data in the impossible state).
- Tables follow snake_case; columns mirror entity fields.

## 6. Deployment

- Build artifacts: `backend/Dockerfile` produces a fat-jar image; each
  SPA Dockerfile produces an nginx image with the static bundle baked in.
- `infra/` holds the Compose file + a single `nginx.conf` that fans out
  `/`, `/admin`, `/api`.
- Deploy: ssh into the prod VM, `git pull`, `docker compose up -d
  --build`. See `infra/` for the compose file and nginx config.

## 7. Cross-cutting decisions

- **Error reason hidden by Spring**: `ResponseStatusException(reason)`
  surfaces only the HTTP status text to clients — the Hebrew reason is
  stripped. SPAs therefore map status code → copy locally
  (see `extractErrorMessage`).
- **No buyer PII in error UI**: a wrong-account / wrong-token error
  must never echo the order owner's email or name back.
- **Cookie banner is notice-only**: essential storage only, no GA or
  third-party pixels. Upgrade to category-based consent only when
  tracking is introduced.

For domain-specific gotchas, see each subpackage's `package-info.java`.
