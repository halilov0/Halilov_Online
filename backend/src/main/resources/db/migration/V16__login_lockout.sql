-- Halilov Online V16: per-account login lockout.
-- failed_login_count increments on every bad-password attempt against an
-- existing account; once the threshold is crossed AuthService sets
-- locked_until to NOW + cooldown, and the next attempt is rejected without
-- a bcrypt check until that time passes. Successful login resets both
-- columns to (0, NULL). Edge nginx rate-limit caps the per-IP rate; this
-- caps the per-account brute-force surface.

ALTER TABLE users
  ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0,
  ADD COLUMN locked_until       TIMESTAMP WITH TIME ZONE;
