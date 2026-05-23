-- Halilov Online V13: invoice share tokens.
-- Lets a registered customer hand a one-time view link to e.g. their
-- accountant without exposing the underlying account password. The owner
-- mints the token via POST /api/orders/{n}/share; the same X-Guest-Token
-- header path validates it on subsequent reads.

ALTER TABLE orders ADD COLUMN share_token VARCHAR(64);

CREATE UNIQUE INDEX idx_orders_share_token ON orders(share_token) WHERE share_token IS NOT NULL;
