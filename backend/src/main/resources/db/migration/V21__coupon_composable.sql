-- Halilov Market V21: composable coupons.
--
-- A coupon used to carry a single (type, value) pair. It now carries any
-- mix of independent benefits — percent off, fixed agorot off, and free
-- shipping — so admin can build a "super coupon" (e.g. 20% + ₪20 + free
-- delivery) in one code. Added knobs:
--   * max_discount_agorot — caps the subtotal discount (mostly for PERCENT)
--   * active_from         — scheduled start, paired with the existing expires_at
--   * once_per_customer   — each customer may redeem the code once
-- The subtotal discount (percent + fixed, capped) still lands in
-- orders.discount_agorot; free shipping just zeroes orders.shipping_agorot,
-- so existing order/invoice/email readers need no change.

ALTER TABLE coupons
    ADD COLUMN percent_off          INT,
    ADD COLUMN fixed_off_agorot     INT,
    ADD COLUMN free_shipping        BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN max_discount_agorot  INT,
    ADD COLUMN once_per_customer    BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN active_from          TIMESTAMPTZ;

-- Backfill the old single-benefit rows into the new columns.
UPDATE coupons SET percent_off      = value WHERE type = 'PERCENT';
UPDATE coupons SET fixed_off_agorot = value WHERE type = 'FIXED';

-- Drop the old model. Dropping the columns also drops the CHECK constraints
-- that depended on them (coupons_type_chk, coupons_percent_range_chk, and the
-- inline value>0 check).
ALTER TABLE coupons
    DROP COLUMN type,
    DROP COLUMN value;

-- Guard rails on the new shape.
ALTER TABLE coupons
    ADD CONSTRAINT coupons_percent_range_chk
        CHECK (percent_off IS NULL OR percent_off BETWEEN 1 AND 100),
    ADD CONSTRAINT coupons_fixed_pos_chk
        CHECK (fixed_off_agorot IS NULL OR fixed_off_agorot > 0),
    ADD CONSTRAINT coupons_max_discount_pos_chk
        CHECK (max_discount_agorot IS NULL OR max_discount_agorot > 0),
    -- A coupon must do at least one thing.
    ADD CONSTRAINT coupons_has_benefit_chk
        CHECK (percent_off IS NOT NULL OR fixed_off_agorot IS NOT NULL OR free_shipping = TRUE);
