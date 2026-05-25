-- Halilov Online V15: password reset tokens.
-- One row per outstanding (or used) reset request. Token is the URL-safe
-- random string the user is emailed; we look up by it, validate not-expired
-- + not-used, and let them set a new password. Single-use by design — once
-- used_at is set the token can no longer be redeemed.

CREATE TABLE password_reset_tokens (
  id         BIGSERIAL PRIMARY KEY,
  token      VARCHAR(64) NOT NULL UNIQUE,
  user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  used_at    TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_user_id ON password_reset_tokens (user_id, created_at DESC);
