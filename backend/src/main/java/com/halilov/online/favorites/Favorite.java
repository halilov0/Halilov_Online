package com.halilov.online.favorites;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One row in the {@code favorites} table — a single (user, product)
 * pairing. The SPA replaces the whole user's set on every PUT, so rows
 * are short-lived and there's no need for ordering or per-row history.
 */
@Entity
@Table(name = "favorites")
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Favorite() {}

    public Favorite(Long userId, Long productId) {
        this.userId = userId;
        this.productId = productId;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Instant getCreatedAt() { return createdAt; }
}
