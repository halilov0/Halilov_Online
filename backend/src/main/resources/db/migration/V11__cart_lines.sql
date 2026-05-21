-- Per-user persisted cart. Cart still lives in localStorage while shopping,
-- but is synced to the DB so a login on another device can merge with what
-- the user left behind.
--
-- Identity is (user_id, product_id) — at most one row per product per user.

CREATE TABLE cart_lines (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id      BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity        INT NOT NULL CHECK (quantity > 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, product_id)
);

CREATE INDEX idx_cart_lines_user ON cart_lines(user_id);
