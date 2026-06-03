# Payments & Receipts — Build Handoff

> **Status: Phases 1–4b PROVEN e2e in SANDBOX; COMMITTED (`451e4b3`) + DARK-DEPLOYED
> to prod** (code live, `PAYMENT_PROVIDER=disabled` → inert). Sandbox browser flow
> passed: card-first checkout → PayPal approve → server capture → idempotent PAID →
> Green Invoice קבלה (#80003) → payment row + order `invoice_number` mirror + retry
> sweep + receipt link to the SPA. **Launch decision (2026-06-03): MANUAL receipts.**
> Green Invoice's API needs a paid plan, so at launch the GI creds stay empty and the
> קבלה is issued by hand (see "Manual receipt mode" below); payments still go live on
> PayPal. **Remaining: Phase 5 go-live** (live PayPal creds + flip URLs) and Phase 6
> (refunds + switch receipts back to the GI API). _Last updated: 2026-06-03._
>
> **Bugs found + fixed during the sandbox e2e (all in this uncommitted change):**
> 1. V20 `currency CHAR(3)` → `VARCHAR(3)` — Hibernate `ddl-auto: validate`
>    refused to boot (`bpchar` ≠ String/varchar).
> 2. Local-dev CORS — vite (5173) ≠ backend (8080) is cross-origin; set
>    `CORS_ALLOWED_ORIGINS=http://localhost:5173` in `infra/.env`. **Prod is
>    same-origin via nginx and needs no allowlist** — dev-only.
> 3. PayPal `application_context.landing_page` = **`BILLING`** (card-first), NOT
>    `GUEST_CHECKOUT` (that value only exists in the newer `experience_context`).
> 4. `ReceiptService` self-invocation bypassed `@Transactional` → the order
>    `invoice_number` mirror was lost. GI-result writes moved into
>    `PaymentRecorder` (separate bean → real tx, atomic with the order update).

This file is the live tracker for wiring real payment + a legal Israeli receipt
into Halilov Online. Permanent architecture lives in [ARCHITECTURE.md](ARCHITECTURE.md)
§3.2; this file is the build status + resume guide.

---

## TL;DR decisions

- **Gateway = PayPal** (Orders v2). No monthly fee, supports **ILS**, IL-supported,
  withdraws to Israeli bank. Pivoted from **Grow/Meshulam** (monthly דמי מנוי too
  heavy for low starting volume). **Stripe rejected** — not officially supported in
  Israel, doesn't settle in ILS, only path is a fake US LLC. Don't revisit unless
  Stripe officially adds Israel.
- **Receipt = Green Invoice ("morning")** — our backend issues a **קבלה (doc type
  400)** when an order goes PAID. Halilov is an **עוסק פטור** ⇒ **no VAT**, never a
  חשבונית מס. **PayPal Invoicing is NOT a valid Israeli קבלה — do not use it** for
  the legal document (free, but not רשות-המסים-compliant). _Launch issues the קבלה
  **manually** (the GI API needs a paid plan); backend auto-issuance is fully wired
  and turns on by just adding the GI creds — see "Manual receipt mode"._
- **Provider-neutral** design: `payments.provider` column + `app.payment.provider`
  switch. An Israeli gateway (for Bit) can be slotted in later with no order/receipt
  rework.
- **Backend owns receipt issuance** (single source of truth = the order/payment row),
  not the gateway's native bridge.

---

## ▶ What Idan needs to do

1. ~~**PayPal sandbox** — Client ID + Secret.~~ ✅ done; in `infra/.env`.
2. ~~**Green Invoice sandbox** — key id + secret.~~ ✅ done; in `infra/.env`
   (base `https://sandbox.d.greeninvoice.co.il/api/v1`).
3. **PayPal live** (for go-live, Phase 5) — verified **PayPal Business** account;
   create a live app → live Client ID + Secret; register a webhook on
   `https://halilov.co.il/api/payments/paypal/webhook` → note the **Webhook ID**.
4. ~~**Green Invoice live** — prod key id + secret.~~ **Deferred — manual receipts at
   launch** (the GI API needs a paid plan). Account stays **עוסק פטור 325350643 /
   עידן חלילוב**; receipts are issued by hand until volume justifies the API plan
   (see "Manual receipt mode"). Switch back later with no code change.

> Creds live in `infra/.env` only (gitignored) — never committed.

---

## ✅ Phase 1 — DONE (schema + config, no behavior change)

| File | What |
|------|------|
| `backend/src/main/resources/db/migration/V20__payments.sql` | `payments` table (system of record). |
| `backend/src/main/resources/application.yml` | `app.payment.paypal.*` + `app.greenInvoice.*`. |
| `infra/docker-compose.prod.yml` | `PAYPAL_*` / `GREEN_INVOICE_*` env passthrough. |
| `infra/.env.example` | Documented the new vars. |
| `backend/src/main/java/com/halilov/online/payment/PaymentService.java` | Javadoc → PayPal (logic still mock). |

`PAYMENT_PROVIDER` stays `mock` (dev) / `disabled` (prod). V20 applies on next boot.

### `payments` table column map (V20)

| column | meaning | PayPal value |
|--------|---------|--------------|
| `provider` | gateway id | `'paypal'` |
| `kind` | `CHARGE` \| `REFUND` | |
| `status` | `INITIATED`/`PAID`/`FAILED`/`REFUNDED` | |
| `amount_agorot`, `currency` | money | ILS |
| `provider_order_id` | gateway session/order id | PayPal **order id** |
| `provider_txn_id` | settled txn id — **idempotency + refund anchor** | PayPal **capture id** |
| `gi_status` | `PENDING`/`ISSUED`/`FAILED` (receipt) | |
| `gi_doc_id` / `_number` / `_type` / `_url` | the GI קבלה | type = **400** |
| `raw_callback` | verified gateway payload (audit) | |

Indexes: **unique** `(provider, provider_txn_id)` (idempotency) · partial retry index
on `gi_status IN ('PENDING','FAILED')`.

---

## Build phases

- [x] **1. Schema + config**
- [x] **2. PayPal order + approval redirect** — `PayPalClient` (OAuth token cached,
  `createOrder` intent=CAPTURE/ILS/`custom_id`=orderNumber, return+cancel URLs);
  `PaymentService.paypalInitiate` returns the **`approve`** link as `redirectUrl`
  and `PaymentRecorder.startCharge` inserts the `INITIATED` row. Frontend already
  redirects on an http(s) `redirectUrl` (`CheckoutPage`).
- [x] **3. Capture + webhook verify** — `POST /api/payments/paypal/{n}/capture`
  (return path, primary) + public signature-verified `POST /api/payments/paypal/webhook`
  (redundant). `PaymentRecorder.confirmPaid` does the **idempotent** flip → PAID
  (stock/email/coupon via `OrderService.markPaidByPayment`), stores the capture id;
  unique `(provider,provider_txn_id)` guards replays. Capture is idempotent (422 →
  GET existing capture). New `shop/src/pages/PaymentReturnPage.tsx` drives it.
- [x] **4. Green Invoice קבלה** — `GreenInvoiceClient` (JWT cached ~1h) issues type
  **400**, income lines `vatType=EXEMPT (2)` (no VAT), shipping line, negative
  discount line, payment type PayPal (5). `ReceiptService` runs **after** the PAID
  commit; `ReceiptRetryJob` sweeps `gi_status PENDING/FAILED`. `gi_doc_id/number/url`
  stored on the row; number mirrored to `orders.invoice_number`. **Validated live in
  sandbox** (docs 80001/80002, `vatRate:0`).
- [x] **4b. Legal wording** — `InvoicePage` title → `סיכום הזמנה`, fine print says
  "not a tax document", and a **הקבלה הרשמית** button links the GI doc via new
  `GET /api/payments/{n}/receipt`.
- [ ] **5. Go-live (manual receipts)**
  - Prod `.env`: set live **PayPal** creds + `PAYPAL_WEBHOOK_ID`,
    `PAYPAL_BASE_URL=https://api-m.paypal.com`,
    `PAYMENT_RETURN_BASE_URL=https://halilov.co.il`, `RECEIPT_MANUAL_NOTICE=true`,
    flip `PAYMENT_PROVIDER=paypal`, redeploy. **Leave the GI creds empty** (manual
    receipts — see "Manual receipt mode"). Do **one ₪-small real end-to-end charge**,
    confirm PAID + the "within a business day" email, then issue the קבלה by hand and
    mark it on the order before opening.
- [ ] **6. Refunds (later)**
  - On `REFUNDED`: PayPal refund `POST /v2/payments/captures/{capture_id}/refund` +
    GI **credit document**; insert a `REFUND` payments row.

---

## Manual receipt mode (lean launch — current)

Green Invoice's **API** requires a paid plan, so for launch we **defer automated
issuance** and create the קבלה **by hand** in morning's free tier. Payments still go
live on PayPal — only receipt issuance is manual. The code needs **no special flag**
for issuance: with the GI creds empty, `ReceiptService` and `ReceiptRetryJob` no-op
(`gi.isConfigured()` guards), so a captured charge sits `gi_status=PENDING` with no
errors and no retry storm.

**Flow:**
1. Customer pays → order PAID → confirmation email. With `RECEIPT_MANUAL_NOTICE=true`
   the email adds *"הקבלה הרשמית תישלח אליך במייל בנפרד תוך יום עסקים."*
2. Admin worklist: the orders list flags paid orders missing a receipt
   ("N ממתינות לקבלה" + a `קבלה` chip per row); the order page shows "ממתין לקבלה ידנית".
3. Admin issues the קבלה by hand in morning, emails it to the customer, then records
   it on the order page (**סמן נשלחה** → number + optional public link):
   `POST /api/admin/orders/{n}/receipt` → `PaymentService.adminMarkReceipt` →
   `markGiIssued` flips the charge to `gi_status=ISSUED`, mirrors the number to
   `orders.invoice_number`, and (if a URL was given) lights up the **הקבלה הרשמית**
   link on the invoice page. Audited as `RECEIPT_ISSUED_MANUAL`.

**Config:** leave `GREEN_INVOICE_API_KEY_ID/SECRET` empty + set `RECEIPT_MANUAL_NOTICE=true`.

**Legal note:** strict law issues the קבלה *at* payment; a daily ≤24h manual batch is
common for small עוסקים but not strictly by-the-book. The automated GI path is the
compliant version — this is a deliberate, reversible cost trade.

### Switching back to the automated GI API (later)
1. **First** mark every already-hand-issued paid order as ISSUED (via **סמן נשלחה**)
   — otherwise step 3 re-issues them as **duplicate קבלות**.
2. Fill `GREEN_INVOICE_API_KEY_ID/SECRET` (+ `GREEN_INVOICE_BASE_URL` prod), set
   `RECEIPT_MANUAL_NOTICE=false`, redeploy.
3. `ReceiptRetryJob` auto-issues any remaining `gi_status=PENDING` charges; new orders
   issue instantly on PAID. Nothing else changes.

---

## API references

- **PayPal Orders v2** — developer.paypal.com/docs/api/orders/v2.
  OAuth `/v1/oauth2/token` · capture `/v2/checkout/orders/{id}/capture` ·
  verify webhook `/v1/notifications/verify-webhook-signature` ·
  refund `/v2/payments/captures/{id}/refund`.
  Base: sandbox `https://api-m.sandbox.paypal.com`, prod `https://api-m.paypal.com`.
- **Green Invoice (morning)** — greeninvoice.docs.apiary.io.
  Token `/api/v1/account/token` · documents `/api/v1/documents` · **type 400 = קבלה**.
  Base: sandbox `https://sandbox.d.greeninvoice.co.il/api/v1` (verify the host in your
  account), prod `https://api.greeninvoice.co.il/api/v1`.

---

## Guardrails (don't break these)

- **Guest + auth**: every payment endpoint accepts an auth session **or** an
  `X-Guest-Token` (see ARCHITECTURE §3.2). The webhook is public but
  **signature-verified**.
- **Idempotency**: never issue a 2nd קבלה / never double-decrement stock — key off
  `provider_txn_id`.
- **עוסק פטור**: no VAT lines anywhere; never call the document חשבונית מס; never
  reintroduce `בע"מ` wording (Idan is an individual עוסק פטור, not a Ltd).
- **Retention**: don't hard-delete a PAID order (must keep the קבלה) — enforce
  app-side, not via DB cascade.
- **Secrets**: env only, never committed.

---

## How to test against sandbox (local)

`infra/.env` already has the sandbox creds + `PAYMENT_PROVIDER=paypal` +
`PAYMENT_RETURN_BASE_URL=http://localhost:5173`.

1. Backend + Postgres (V20 applies on boot), then `cd shop && npm run dev`.
2. Add to cart → checkout → **אישור הזמנה**. You're redirected to the PayPal
   **sandbox** page — it lands on the **card form** (`landing_page=BILLING`), so
   you can pay by card with no account. To pay via PayPal instead, log in with
   the **Personal/buyer** sandbox account (developer.paypal.com → *Testing Tools
   → Sandbox Accounts* → the `…@personal.example.com` one). **Do NOT use the
   `…@business.example.com` (seller) account** — PayPal blocks paying yourself
   ("אתם נכנסים לחשבונו של המוכר") and the order stays PENDING.
3. You land on `/payment/return`, which captures server-side → order flips PAID
   → the GI **קבלה** is issued. Confirm: order shows PAID, `payments` row is
   `PAID` + `gi_status=ISSUED`, and `/invoice/{n}` shows the **הקבלה הרשמית** button.

> The webhook can't reach localhost — the **return-path capture** is the local
> trigger. The webhook (and `PAYPAL_WEBHOOK_ID`) only matter once deployed to a
> public HTTPS host. Server→gateway calls (OAuth, createOrder, GI token+document)
> were already validated against sandbox via curl.

## How to resume / go-live (Phase 5)

Read this file + the memory note `project_payment_invoice_build.md`. For go-live:
get the live PayPal Business creds + webhook id and live GI key (see "What Idan
needs to do"), then in prod `.env` set the creds, `PAYPAL_BASE_URL=https://api-m.paypal.com`,
`GREEN_INVOICE_BASE_URL=https://api.greeninvoice.co.il/api/v1`,
`PAYMENT_RETURN_BASE_URL` (or `SITE_BASE_URL`) = `https://halilov.co.il`, flip
`PAYMENT_PROVIDER=paypal`, redeploy. Do one ₪-small real end-to-end charge, confirm
the קבלה issued, then open. Keep ARCHITECTURE.md §3.2 + `backend/README.md` in sync.
