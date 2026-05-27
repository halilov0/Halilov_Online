package com.halilov.online.notification;

/**
 * Request body for {@code POST /api/products/{id}/stock-notify}.
 * Email is optional for logged-in users (resolved from the JWT) and
 * required for anonymous callers.
 */
public final class StockNotificationDtos {
    private StockNotificationDtos() {}

    public record SubscribeRequest(String email) {}
}
