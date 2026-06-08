package com.halilov.online.coupon;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Request and response records for coupon endpoints — admin upsert
 * shape, list view (with a derived Hebrew {@code summary}), and the
 * {@code /validate} preview returned to the cart.
 */
public class CouponDtos {

    public record CouponUpsert(
        @NotBlank @Size(max = 64) String code,
        @Min(1) @Max(100) Integer percentOff,
        @Min(1) Integer fixedOffAgorot,
        boolean freeShipping,
        @Min(1) Integer maxDiscountAgorot,
        @Min(0) int minSubtotalAgorot,
        @Min(1) Integer maxUses,
        boolean oncePerCustomer,
        Instant activeFrom,
        Instant expiresAt,
        boolean active
    ) {}

    public record CouponView(
        Long id,
        String code,
        Integer percentOff,
        Integer fixedOffAgorot,
        boolean freeShipping,
        Integer maxDiscountAgorot,
        int minSubtotalAgorot,
        Integer maxUses,
        boolean oncePerCustomer,
        int usedCount,
        Instant activeFrom,
        Instant expiresAt,
        boolean active,
        String summary,
        Instant createdAt
    ) {
        public static CouponView from(Coupon c) {
            return new CouponView(
                c.getId(), c.getCode(), c.getPercentOff(), c.getFixedOffAgorot(),
                c.isFreeShipping(), c.getMaxDiscountAgorot(), c.getMinSubtotalAgorot(),
                c.getMaxUses(), c.isOncePerCustomer(), c.getUsedCount(),
                c.getActiveFrom(), c.getExpiresAt(), c.isActive(),
                summarize(c.getPercentOff(), c.getFixedOffAgorot(), c.isFreeShipping(),
                          c.getMaxDiscountAgorot()),
                c.getCreatedAt()
            );
        }
    }

    public record ValidateRequest(
        @NotBlank @Size(max = 64) String code,
        @Min(0) int subtotalAgorot
    ) {}

    public record ValidateResponse(
        String code,
        int discountAgorot,
        boolean freeShipping,
        String summary
    ) {}

    /**
     * Short Hebrew description of what a coupon does, e.g.
     * {@code "20% הנחה (עד ₪50) + משלוח חינם"}. Used by the admin list and
     * the shop's applied-coupon chip so neither has to reconstruct it.
     */
    static String summarize(Integer percentOff, Integer fixedOffAgorot,
                            boolean freeShipping, Integer maxDiscountAgorot) {
        List<String> parts = new ArrayList<>();
        if (percentOff != null) {
            String p = percentOff + "% הנחה";
            if (maxDiscountAgorot != null) p += " (עד ₪" + (maxDiscountAgorot / 100) + ")";
            parts.add(p);
        }
        if (fixedOffAgorot != null) {
            parts.add("₪" + (fixedOffAgorot / 100) + " הנחה");
        }
        if (freeShipping) {
            parts.add("משלוח חינם");
        }
        return String.join(" + ", parts);
    }
}
