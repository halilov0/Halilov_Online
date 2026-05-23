-- Halilov Online V12: guest checkout.
-- Lets shoppers complete an order without registering. The per-order
-- shipping snapshot in `addresses` now allows a null user_id, and `orders`
-- carries the guest's email plus a random token that gates anonymous
-- order lookup / payment-initiation calls.

ALTER TABLE addresses ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE orders ADD COLUMN guest_email VARCHAR(255);
ALTER TABLE orders ADD COLUMN guest_token VARCHAR(64);

CREATE UNIQUE INDEX idx_orders_guest_token ON orders(guest_token) WHERE guest_token IS NOT NULL;
