package com.halilov.online.catalog;

import jakarta.validation.constraints.*;

import java.util.Arrays;
import java.util.List;

/**
 * Request and response records for the catalog controllers.
 * {@code *.from(entity)} helpers project entities into the
 * client-facing view shape.
 */
public class CatalogDtos {

    public record CategoryView(Long id, String slug, String nameHe, Long parentId, int sortOrder) {
        static CategoryView from(Category c) {
            return new CategoryView(c.getId(), c.getSlug(), c.getNameHe(), c.getParentId(), c.getSortOrder());
        }
    }

    public record CategoryUpsert(
        @NotBlank @Size(max = 128) String slug,
        @NotBlank @Size(max = 255) String nameHe,
        Long parentId,
        @Min(0) int sortOrder
    ) {}

    /** Bulk activate/hide products: {@code active=true} shows them in the
     *  shop, {@code false} hides them. Capped to match {@code BulkIdsRequest}. */
    public record BulkActiveRequest(
        @NotEmpty @Size(max = 2000) List<Long> ids,
        boolean active
    ) {}

    public record ProductView(
        Long id, String sku, String slug, String nameHe, String descriptionHe,
        Long categoryId, int priceAgorot, int stockQty,
        String imageUrl, List<String> imageUrls, boolean active
    ) {
        static ProductView from(Product p) {
            return new ProductView(
                p.getId(), p.getSku(), p.getSlug(), p.getNameHe(), p.getDescriptionHe(),
                p.getCategoryId(), p.getPriceAgorot(), p.getStockQty(),
                p.getImageUrl(), splitUrls(p.getImageUrls()), p.isActive()
            );
        }
    }

    public record ProductUpsert(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 255) String slug,
        @NotBlank @Size(max = 255) String nameHe,
        @Size(max = 10000) String descriptionHe,
        Long categoryId,
        @Min(0) int priceAgorot,
        @Min(0) int stockQty,
        @Size(max = 1024) String imageUrl,
        List<@Size(max = 1024) String> imageUrls,
        boolean active
    ) {}

    public record ImportRowError(int line, String sku, String message) {}

    public record ImportResult(int created, int updated, int totalRows, List<ImportRowError> errors) {}

    static List<String> splitUrls(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("\\R"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    static String joinUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) return null;
        String joined = urls.stream()
            .filter(u -> u != null && !u.isBlank())
            .map(String::trim)
            .reduce((a, b) -> a + "\n" + b)
            .orElse(null);
        return joined;
    }
}
