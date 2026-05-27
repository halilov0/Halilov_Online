package com.halilov.online.coupon;

import jakarta.validation.constraints.*;

import java.time.Instant;

/**
 * Request and response records for coupon endpoints — admin upsert
 * shape, list view, and the {@code /validate} preview returned to the
 * cart.
 */
public class CouponDtos {

    public record CouponUpsert(
        @NotBlank @Size(max = 64) String code,
        @NotNull CouponType type,
        @Min(1) int value,
        @Min(0) int minSubtotalAgorot,
        @Min(1) Integer maxUses,
        Instant expiresAt,
        boolean active
    ) {}

    public record CouponView(
        Long id,
        String code,
        CouponType type,
        int value,
        int minSubtotalAgorot,
        Integer maxUses,
        int usedCount,
        Instant expiresAt,
        boolean active,
        Instant createdAt
    ) {
        public static CouponView from(Coupon c) {
            return new CouponView(
                c.getId(), c.getCode(), c.getType(), c.getValue(),
                c.getMinSubtotalAgorot(), c.getMaxUses(), c.getUsedCount(),
                c.getExpiresAt(), c.isActive(), c.getCreatedAt()
            );
        }
    }

    public record ValidateRequest(
        @NotBlank @Size(max = 64) String code,
        @Min(0) int subtotalAgorot
    ) {}

    public record ValidateResponse(
        String code,
        CouponType type,
        int value,
        int discountAgorot
    ) {}
}
