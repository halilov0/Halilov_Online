-- Per-user persisted favorites (wishlist). Same continuous-sync model as
-- cart_lines: the SPA mirrors the set in localStorage while the user
-- shops, and PUTs the canonical set to the server on every change so a
-- second device sees the same hearted products.
--
-- No quantity column — a product is either favorited or not. Identity is
-- (user_id, product_id) — at most one row per product per user.

CREATE TABLE favorites (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id      BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, product_id)
);

CREATE INDEX idx_favorites_user ON favorites(user_id);
