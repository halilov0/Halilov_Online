package com.halilov.online.notification;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * "Notify me when this is back in stock" sign-up. Rows are inserted
 * by {@link StockNotificationService#subscribe} and consumed (one-shot
 * — {@code notified_at} stamped) when the product's stock transitions
 * from zero to positive.
 */
@Entity
@Table(name = "stock_notifications")
public class StockNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "notified_at")
    private Instant notifiedAt;

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getNotifiedAt() { return notifiedAt; }
    public void setNotifiedAt(Instant notifiedAt) { this.notifiedAt = notifiedAt; }
}
