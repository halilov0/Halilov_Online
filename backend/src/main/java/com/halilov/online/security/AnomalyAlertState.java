package com.halilov.online.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Per-(kind, scope) cooldown record for {@link AnomalyMonitor}. The
 * key encodes the alert kind and the per-IP / per-account scope. The
 * stamped {@code last_fired_at} is what suppresses re-alerts within
 * the monitor's cooldown window.
 */
@Entity
@Table(name = "anomaly_alerts")
public class AnomalyAlertState {

    @Id
    @Column(name = "alert_key", length = 255)
    private String alertKey;

    @Column(name = "last_fired_at", nullable = false)
    private Instant lastFiredAt;

    public AnomalyAlertState() {}

    public AnomalyAlertState(String alertKey, Instant lastFiredAt) {
        this.alertKey = alertKey;
        this.lastFiredAt = lastFiredAt;
    }

    public String getAlertKey() { return alertKey; }
    public void setAlertKey(String alertKey) { this.alertKey = alertKey; }

    public Instant getLastFiredAt() { return lastFiredAt; }
    public void setLastFiredAt(Instant lastFiredAt) { this.lastFiredAt = lastFiredAt; }
}
