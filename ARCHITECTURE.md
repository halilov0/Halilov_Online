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
| `favorites`     | Server-backed wishlist for signed-in users (`favorites` table).              |
| `coupon`        | Composable discount codes (% + ₪ + free-shipping mix, cap, once-per-customer, scheduling), usage counters, validation. |
| `order`         | Order lifecycle, addresses, delivery method/pricing, refunds, CSV export.    |
| `payment`       | Payment orchestration + `payments` table. PayPal (Orders v2) capture + Green Invoice קבלה (type 400); `mock`/`disabled` fallbacks. Provider-neutral — see [PAYMENTS_BUILD.md](PAYMENTS_BUILD.md). |
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
- **Self-modify-own-credential pattern** (`PATCH /api/me/password` and
  the password-reset flow): rotate `users.force_logout_at = now()` to
  invalidate every previously-issued JWT, then issue a fresh token in
  the response so the calling tab survives. Other devices die on their
  next request. Re-use for any future endpoint where the user mutates
  their own credentials (email change, 2FA disable, etc.).
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
client                backend                    PayPal           Green Invoice
  │   POST /orders     │                            │                    │
  │ ─────────────────▶ │  create Order(PENDING),    │                    │
  │                    │  mint guest_token if guest │                    │
  │                    │                            │                    │
  │   POST /orders/{n}/pay                          │                    │
  │ ─────────────────▶ │  createOrder (Orders v2) ─▶│  payments row      │
  │                    │  INITIATED + approve URL   │  INITIATED         │
  │   redirect to PayPal approve ◀───────────────── │                    │
  │ ══ approve on PayPal ══════════════════════════▶│                    │
  │   return to /payment/return?token=…&order=…     │                    │
  │   POST /payments/paypal/{n}/capture             │                    │
  │ ─────────────────▶ │  capture ─────────────────▶│  (capture id)      │
  │                    │  Order → PAID, decrement   │                    │
  │                    │  stock, email, coupon;     │                    │
  │                    │  payments row PAID         │                    │
  │                    │  issue קבלה (type 400) ───────────────────────▶ │
  │                    │  ◀── doc id/number/url ──────────────────────── │
  │  (signature-verified webhook is a redundant, idempotent confirmation) │
```

- **PENDING → PAID** is the *only* transition that decrements stock and
  bumps coupon usage. Cancels/refunds reverse both.
- **Guest checkout**: an order with no `user_id` gets a random
  `guest_token` returned once at create time. Anonymous reads, the
  capture-on-return call, and the receipt lookup require the token via
  `X-Guest-Token` header (or `?t=` on the return + share links).
- **Share token**: a registered user can mint a `share_token` for an
  order they own (idempotent — same token returned on repeat). Lets the
  buyer email the invoice link to an accountant without sharing
  credentials.
- **Ownership errors**: when a logged-in user requests an order they
  don't own, we return **403 (Forbidden)**, not 404. Distinguishes
  "wrong account" from "wrong order number" in the SPA without leaking
  the owner's identity.

#### Real payments & receipts

Selected by `app.payment.provider`: **`paypal`** (real money), **`mock`** (the
in-app fake checkout at `/payment/mock`, dev only), or **`disabled`** (hard-stop
— `/pay` returns 503). Sandbox/go-live status in [PAYMENTS_BUILD.md](PAYMENTS_BUILD.md).

- **Gateway = PayPal** (Orders v2). `POST /orders/{n}/pay` creates the order and
  returns its payer-approval URL (same `redirectUrl` contract as mock); the SPA
  redirects, the payer approves, PayPal returns to `/payment/return`, and
  `POST /payments/paypal/{n}/capture` captures server-side. A signature-verified
  `POST /payments/paypal/webhook` is a redundant, idempotent confirmation.
  **Provider-neutral** (`payments.provider` + `app.payment.provider`) so an
  Israeli gateway can be added later (e.g. for Bit) without touching the
  order/receipt flow.
- **System of record = `payments` table** (V20) — one row per money movement
  (CHARGE/REFUND) plus the receipt it produced. `provider_order_id` = PayPal
  order id; `provider_txn_id` = capture id. Idempotency anchor: unique
  `(provider, provider_txn_id)` so a replayed return/webhook can't double-pay or
  double-issue. The PAID flip + payment-row write happen in one transaction
  (`PaymentRecorder`); receipt issuance runs **after** that commit.
- **Legal receipt = Green Invoice (morning)** — issued backend-side as a
  **קבלה (doc type 400)** when the order goes PAID, with income lines
  `vatType=EXEMPT`. Halilov is an עוסק פטור, so the document carries **no VAT**
  and is never a חשבונית מס. A GI outage never fails a paid order
  (`gi_status` PENDING/FAILED → `ReceiptRetryJob` sweep). The SPA's
  `/invoice/{n}` is an order **summary** (not a tax doc); the קבלה link is
  fetched from `GET /payments/{n}/receipt`. PayPal's own invoicing is **not** a
  valid Israeli קבלה, so it is not used for the legal document. **Launch runs in
  manual-receipt mode** (the GI API needs a paid plan): with the GI creds empty,
  auto-issuance no-ops and the admin records a hand-issued קבלה via
  `POST /api/admin/orders/{n}/receipt` (marks the charge ISSUED so the API,
  when later enabled, won't duplicate it). See [PAYMENTS_BUILD.md](PAYMENTS_BUILD.md).

### 3.3 Cart

Signed-in users have server-backed cart lines (`cart_lines` table).
The client treats the server as the source of truth and uses:

- **Debounced PUT** of full cart state from active tab.
- **`BroadcastChannel` + `storage` events** to mirror changes across tabs
  on the same device.
- **`visibilitychange` refetch** to pick up cross-device changes when a
  tab returns to foreground.

Guests keep the cart entirely in `localStorage`, tagged with an owner
userId and a *baseline* (the last cart that provably matched the server)
once signed in. On login the cart is reconciled by that tag: a leftover
cart belonging to a different user is dropped, otherwise login merges
only the lines **added since the baseline**. A genuine guest cart has an
empty baseline, so it folds in whole; the residue of an expired session
contributes only items added during the logged-out window — its lines
already in the DB are skipped, so the cart never doubles.

See [CART.md](CART.md) for the full cart behavior: storage keys, sync,
the login reconciliation table, worked scenarios, and deliberate edges.

### 3.4 Favorites (wishlist)

Same continuous-sync model as the cart, applied to a per-user set of
product ids (`favorites` table). No quantity dimension — a product is
either hearted or not. The frontend uses the same primitives: debounced
`PUT /api/favorites`, `BroadcastChannel` cross-tab fan-out,
`visibilitychange` reconcile, `pagehide` keepalive flush, owner +
baseline localStorage keys for login reconciliation. Server-side drops
for missing/inactive products surface as `REMOVED` adjustments (toast +
inline notice on the favorites page), so a hearted product never
vanishes silently.

See [FAVORITES.md](FAVORITES.md) for the differences from cart.

### 3.5 Money & taxes

- All prices are stored as **integer agorot** (NIS × 100). No floats.
- Halilov Online is registered as a **עוסק פטור** — VAT-exempt. New
  orders persist `vat_agorot = 0`. The column survives for historical
  records pre-exemption.

### 3.6 Media

`MediaStorage` is an interface with two implementations:

- `LocalFileMediaStorage` — disk under `media/`, served via
  `MediaWebConfig`. Used in dev and as the prod default.
- `R2MediaStorage` — Cloudflare R2 via the AWS S3 SDK. Used when
  `app.media.r2.*` is configured.

`ImageProcessor` runs single-image transforms on upload (resize,
re-encode). No multi-image composition.

### 3.7 Audit & anomaly

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
- `favorites/favoritesStore.ts` — wishlist set + debounced sync to backend.
- `delivery/deliveryConfig.ts` — *backend-served* delivery config
  cached on the client (single source of truth lives in `delivery_method`
  table; the client just mirrors it via `/api/delivery/config`).
- `lib/invoicePdf.ts` — client-side PDF render of the `/invoice/{n}` page, which
  is an **order summary** (not a tax doc); the official Green Invoice קבלה is
  linked separately via `GET /api/payments/{n}/receipt`.

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
