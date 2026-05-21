-- Rewrite any legacy PICKUP orders to COURIER and tighten the check
-- constraint to the only method we actually offer.
-- See commit adc8e24 (delivery: drop fake single-pickup-point option).

UPDATE orders SET delivery_method = 'COURIER' WHERE delivery_method = 'PICKUP';

ALTER TABLE orders DROP CONSTRAINT orders_delivery_method_chk;
ALTER TABLE orders
    ADD CONSTRAINT orders_delivery_method_chk
        CHECK (delivery_method IN ('COURIER'));
