package com.halilov.online.favorites;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class FavoriteDtos {
    private FavoriteDtos() {}

    /** Client-supplied desired favorites set — for PUT /api/favorites and POST /api/favorites/merge. */
    public record FavoritesReplaceRequest(
        // Bound the payload. A real wishlist doesn't have 500+ items;
        // an unbounded list is needless work / a soft DoS vector.
        @NotNull @Size(max = 500) List<@NotNull Long> productIds
    ) {}

    /**
     * A change the server made to the desired favorites that the client
     * didn't ask for, so the SPA can surface it instead of mutating the
     * set silently. Only {@code REMOVED} is used today — products are
     * dropped when they're inactive or deleted (no clamp dimension, since
     * favorites have no quantity). {@code nameHe} is null when the product
     * no longer exists at all.
     */
    public enum AdjustmentType { REMOVED }

    public record FavoriteAdjustment(
        Long productId,
        String nameHe,
        AdjustmentType type
    ) {}

    /** Canonical favorites set plus any server-side adjustments. */
    public record FavoritesResponse(
        List<Long> productIds,
        List<FavoriteAdjustment> adjustments
    ) {
        static FavoritesResponse of(List<Long> productIds) {
            return new FavoritesResponse(productIds, List.of());
        }
    }
}
