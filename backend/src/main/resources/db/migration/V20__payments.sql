-- Halilov Online V20: real payments + Green Invoice receipts.
--
-- Replaces the mock payment path. Each row is one money movement against an
-- order — a CHARGE via the gateway (PayPal today; the design is provider-neutral
-- so an Israeli gateway can be added later), and later a REFUND — together with
-- the Green Invoice document it produced: a קבלה (receipt, GI type 400) for a
-- charge, a credit document for a refund. Halilov is an עוסק פטור, so the GI
-- document never carries VAT (orders.vat_agorot stays 0).
--
-- This table is the system of record and the idempotency anchor for the
-- gateway callback. `orders.payment_ref` + `orders.invoice_number` (already
-- present) stay as denormalized convenience mirrors of the latest charge so
-- existing admin / invoice reads keep working.
--
-- FK is ON DELETE CASCADE for referential simplicity (matches the rest of the
-- schema). The "never hard-delete a PAID order / never drop an issued קבלה"
-- retention rule is enforced at the application layer, not the DB — abandoned
-- INITIATED/FAILED attempts stay freely deletable.

CREATE TABLE payments (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,

    provider        VARCHAR(32)  NOT NULL,                      -- 'paypal'
    kind            VARCHAR(16)  NOT NULL DEFAULT 'CHARGE',     -- CHARGE | REFUND
    status          VARCHAR(24)  NOT NULL DEFAULT 'INITIATED',  -- INITIATED | PAID | FAILED | REFUNDED
    amount_agorot   INTEGER      NOT NULL,
    currency        VARCHAR(3)   NOT NULL DEFAULT 'ILS',

    -- Gateway identifiers, provider-neutral:
    --   provider_order_id = the checkout/order session id the gateway returns
    --       when we start the payment (PayPal Orders-v2 order id).
    --   provider_txn_id   = the captured/settled transaction id confirmed
    --       server-side (PayPal capture id) — the idempotency + refund anchor.
    provider_order_id VARCHAR(128),
    provider_txn_id   VARCHAR(128),

    -- Green Invoice document issued for this movement. gi_status tracks issuance
    -- independently of the charge so a GI outage never fails a paid order: the
    -- charge commits PAID, the receipt is retried from PENDING/FAILED. NULL = no
    -- issuance attempted yet (charge not PAID).
    gi_status       VARCHAR(24),                                -- PENDING | ISSUED | FAILED
    gi_doc_id       VARCHAR(64),
    gi_doc_number   VARCHAR(64),
    gi_doc_type     INTEGER,                                    -- 400 = קבלה
    gi_doc_url      VARCHAR(512),

    raw_callback    TEXT,                                       -- verified gateway payload, kept for audit

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at         TIMESTAMPTZ
);

CREATE INDEX idx_payments_order ON payments(order_id);

-- Idempotency: a verified gateway transaction maps to exactly one charge row,
-- so a replayed webhook can't issue a second קבלה or double-decrement stock.
CREATE UNIQUE INDEX idx_payments_provider_txn
    ON payments(provider, provider_txn_id)
    WHERE provider_txn_id IS NOT NULL;

-- Retry sweep target: paid charges whose receipt hasn't been issued yet.
CREATE INDEX idx_payments_gi_retry
    ON payments(gi_status)
    WHERE gi_status IN ('PENDING', 'FAILED');
