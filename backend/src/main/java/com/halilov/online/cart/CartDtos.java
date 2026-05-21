package com.halilov.online.cart;

import com.halilov.online.catalog.Product;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class CartDtos {
    private CartDtos() {}

    /** Client-supplied desired cart state — for PUT /api/cart and POST /api/cart/merge. */
    public record CartUpsertItem(
        @NotNull Long productId,
        @Min(1) int quantity
    ) {}

    public record CartReplaceRequest(
        @NotNull List<@NotNull CartUpsertItem> items
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
}
