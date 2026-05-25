-- Halilov Online V14: admin-visible audit log + per-user force-logout watermark.
-- 1) audit_log: append-only trail of meaningful actions across the app
--    (auth, orders, admin edits, account changes). actor_user_id can be NULL
--    for guest/anonymous events; actor_email is a snapshot at write-time so
--    the row stays readable even if the user is later deleted.
-- 2) users.force_logout_at: monotonic watermark. JWTs whose `iat` < this value
--    are rejected by the auth filter — enables admin "force logout" without
--    moving to stateful sessions.

CREATE TABLE audit_log (
  id              BIGSERIAL PRIMARY KEY,
  actor_user_id   BIGINT REFERENCES users(id) ON DELETE SET NULL,
  actor_role      VARCHAR(32),
  actor_email     VARCHAR(255),
  actor_ip        VARCHAR(64),
  action          VARCHAR(64) NOT NULL,
  target_type     VARCHAR(64),
  target_id       VARCHAR(128),
  message         TEXT,
  metadata        TEXT,
  created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_created_at ON audit_log (created_at DESC);
CREATE INDEX idx_audit_log_actor      ON audit_log (actor_user_id, created_at DESC);
CREATE INDEX idx_audit_log_action     ON audit_log (action, created_at DESC);

ALTER TABLE users ADD COLUMN force_logout_at TIMESTAMP WITH TIME ZONE;
