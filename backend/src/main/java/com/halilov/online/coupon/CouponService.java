package com.halilov.online.coupon;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Coupon validation, application, and admin CRUD.
 *
 * <p>{@link #resolveForOrder} is the single hot-path entry point used
 * by the checkout — it normalises the code, checks the active window
 * and the minimum subtotal, computes the discount in agorot, and
 * returns an immutable {@code AppliedCoupon} snapshot. The
 * complementary {@link #incrementUsage} / {@link #decrementUsage} move
 * the usage counter on {@code PENDING → PAID} and on cancel/refund
 * respectively — kept in lockstep with the order state by
 * {@link com.halilov.online.order.OrderService}.
 */
@Service
public class CouponService {

    private final CouponRepository coupons;

    public CouponService(CouponRepository coupons) {
        this.coupons = coupons;
    }

    @Transactional(readOnly = true)
    public List<CouponDtos.CouponView> adminListAll() {
        return coupons.findAllByOrderByCreatedAtDesc().stream()
            .map(CouponDtos.CouponView::from).toList();
    }

    @Transactional
    public CouponDtos.CouponView adminCreate(CouponDtos.CouponUpsert req) {
        String code = normalize(req.code());
        if (coupons.existsByCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "קוד כבר קיים");
        }
        validateTypeValue(req.type(), req.value());
        Coupon c = new Coupon();
        apply(c, req, code);
        return CouponDtos.CouponView.from(coupons.save(c));
    }

    @Transactional
    public CouponDtos.CouponView adminUpdate(Long id, CouponDtos.CouponUpsert req) {
        Coupon c = coupons.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "קוד לא נמצא"));
        String code = normalize(req.code());
        if (!c.getCode().equalsIgnoreCase(code) && coupons.existsByCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "קוד כבר קיים");
        }
        validateTypeValue(req.type(), req.value());
        apply(c, req, code);
        return CouponDtos.CouponView.from(c);
    }

    @Transactional
    public void adminDelete(Long id) {
        if (!coupons.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "קוד לא נמצא");
        }
        coupons.deleteById(id);
    }

    @Transactional(readOnly = true)
    public CouponDtos.ValidateResponse validate(CouponDtos.ValidateRequest req) {
        Coupon c = requireUsable(req.code(), req.subtotalAgorot());
        int discount = computeDiscount(c, req.subtotalAgorot());
        return new CouponDtos.ValidateResponse(c.getCode(), c.getType(), c.getValue(), discount);
    }

    /** Used by OrderService when creating an order with a coupon code. */
    @Transactional(readOnly = true)
    public Optional<AppliedCoupon> resolveForOrder(String rawCode, int subtotalAgorot) {
        if (rawCode == null || rawCode.isBlank()) return Optional.empty();
        Coupon c = requireUsable(rawCode, subtotalAgorot);
        return Optional.of(new AppliedCoupon(c.getCode(), computeDiscount(c, subtotalAgorot)));
    }

    /** Best-effort usage increment after order is paid. Won't fail the parent tx. */
    @Transactional
    public void incrementUsage(String code) {
        if (code == null || code.isBlank()) return;
        coupons.findByCodeIgnoreCase(code).ifPresent(c -> c.setUsedCount(c.getUsedCount() + 1));
    }

    /** Reverse a usage when an order is cancelled/refunded after PAID. */
    @Transactional
    public void decrementUsage(String code) {
        if (code == null || code.isBlank()) return;
        coupons.findByCodeIgnoreCase(code).ifPresent(c -> {
            if (c.getUsedCount() > 0) c.setUsedCount(c.getUsedCount() - 1);
        });
    }

    private Coupon requireUsable(String rawCode, int subtotalAgorot) {
        Coupon c = coupons.findByCodeIgnoreCase(normalize(rawCode))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "קוד לא תקין"));
        if (!c.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "הקוד אינו פעיל");
        }
        if (c.getExpiresAt() != null && c.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "הקוד פג תוקף");
        }
        if (c.getMaxUses() != null && c.getUsedCount() >= c.getMaxUses()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "הקוד מוצה");
        }
        if (subtotalAgorot < c.getMinSubtotalAgorot()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "סכום מינימלי " + (c.getMinSubtotalAgorot() / 100) + " ש\"ח");
        }
        return c;
    }

    private int computeDiscount(Coupon c, int subtotalAgorot) {
        int raw = switch (c.getType()) {
            case PERCENT -> Math.round(subtotalAgorot * (c.getValue() / 100f));
            case FIXED   -> c.getValue();
        };
        return Math.max(0, Math.min(raw, subtotalAgorot));
    }

    private static String normalize(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    private static void validateTypeValue(CouponType type, int value) {
        if (type == CouponType.PERCENT && (value < 1 || value > 100)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "אחוז 1-100 בלבד");
        }
    }

    private static void apply(Coupon c, CouponDtos.CouponUpsert req, String normalizedCode) {
        c.setCode(normalizedCode);
        c.setType(req.type());
        c.setValue(req.value());
        c.setMinSubtotalAgorot(req.minSubtotalAgorot());
        c.setMaxUses(req.maxUses());
        c.setExpiresAt(req.expiresAt());
        c.setActive(req.active());
    }

    public record AppliedCoupon(String code, int discountAgorot) {}
}
