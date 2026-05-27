package com.halilov.online.cart;

import com.halilov.online.catalog.Product;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class CartDtos {
    private CartDtos() {}

    /** Client-supplied desired cart state — for PUT /api/cart and POST /api/cart/merge. */
    public record CartUpsertItem(
        @NotNull Long productId,
        @Min(1) int quantity
    ) {}

    public record CartReplaceRequest(
        // Bound the payload: a real cart never holds hundreds of distinct
        // products, and an unbounded list is needless work / a soft DoS vector.
        @NotNull @Size(max = 200) List<@NotNull CartUpsertItem> items
    ) {}

    /** Server-resolved cart line — product details come from the current catalog,
     *  so prices/names refresh automatically and inactive products drop out. */
    public record CartLineView(
        Long productId,
        String slug,
        String nameHe,
        int priceAgorot,
        int quantity,
        int stockQty,
        String imageUrl
    ) {
        static CartLineView from(Product p, int quantity) {
            return new CartLineView(
                p.getId(), p.getSlug(), p.getNameHe(),
                p.getPriceAgorot(), quantity, p.getStockQty(),
                p.getImageUrl()
            );
        }
    }

    /**
     * A change the server made to the desired cart that the client didn't ask
     * for, so the SPA can surface it instead of mutating the cart silently.
     *
     * <ul>
     *   <li>{@code CLAMPED} — quantity reduced to available stock
     *       ({@code finalQty} &lt; {@code requestedQty}).</li>
     *   <li>{@code REMOVED} — line dropped entirely because the product is
     *       inactive, deleted, or out of stock ({@code finalQty == 0};
     *       {@code nameHe} is null when the product no longer exists).</li>
     * </ul>
     */
    public enum AdjustmentType { CLAMPED, REMOVED }

    public record CartAdjustment(
        Long productId,
        String nameHe,
        AdjustmentType type,
        int requestedQty,
        int finalQty
    ) {}

    /** Canonical cart plus any server-side adjustments made while resolving it. */
    public record CartResponse(
        List<CartLineView> lines,
        List<CartAdjustment> adjustments
    ) {
        static CartResponse of(List<CartLineView> lines) {
            return new CartResponse(lines, List.of());
        }
    }
}
