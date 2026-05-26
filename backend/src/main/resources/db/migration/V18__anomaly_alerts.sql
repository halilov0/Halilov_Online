-- Halilov Online V18: dedupe state for the AnomalyMonitor scheduler.
-- One row per (kind, scope) — e.g. FAILED_LOGINS:admin@halilov.local or
-- CANCEL_SPIKE (global). last_fired_at lets the scheduler suppress repeat
-- alerts within a cooldown window so a sustained attack doesn't spam mail.

CREATE TABLE anomaly_alerts (
  alert_key     VARCHAR(255) PRIMARY KEY,
  last_fired_at TIMESTAMPTZ NOT NULL
);
