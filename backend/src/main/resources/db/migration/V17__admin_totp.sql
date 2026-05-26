-- Halilov Online V17: TOTP-based 2FA for admin accounts.
-- totp_secret holds the base32-encoded shared secret, totp_enabled flips
-- to true after the user confirms their authenticator app with a valid
-- 6-digit code. recovery_codes are one-shot SHA-256 hashes — we never
-- store the plaintext code after enrollment. Login from an IP not in
-- app.admin.trustedIps requires either a TOTP code or one recovery code.

ALTER TABLE users
  ADD COLUMN totp_secret  VARCHAR(64),
  ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE totp_recovery_codes (
  id         BIGSERIAL PRIMARY KEY,
  user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  code_hash  VARCHAR(128) NOT NULL,
  used_at    TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_totp_recovery_user_id ON totp_recovery_codes (user_id);
