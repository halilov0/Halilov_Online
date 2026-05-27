package com.halilov.online.coupon;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.halilov.online.common.InMemoryThrottle;

import java.time.Duration;

/**
 * Public coupon validation under {@code /api/coupons}. The cart calls
 * {@code POST /validate} to preview a discount without committing an
 * order. Throttled per-IP (20/min) on top of the edge nginx
 * 10r/s limit so a botnet can't dictionary-scan coupon codes.
 */
@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    // Caps brute-force enumeration of valid codes. Real users typing/retrying
    // a code stay well under 20/min; a botnet scanning a dictionary does not.
    // Edge nginx already limits 10r/s per IP — this is the per-minute backstop.
    private static final int VALIDATE_MAX_PER_MIN = 20;

    private final CouponService coupons;
    private final InMemoryThrottle throttle;

    public CouponController(CouponService coupons, InMemoryThrottle throttle) {
        this.coupons = coupons;
        this.throttle = throttle;
    }

    @PostMapping("/validate")
    public CouponDtos.ValidateResponse validate(@Valid @RequestBody CouponDtos.ValidateRequest req,
                                                HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        if (!throttle.tryAcquire("coupon-validate:ip", ip,
                VALIDATE_MAX_PER_MIN, Duration.ofMinutes(1))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "rate limited");
        }
        return coupons.validate(req);
    }
}
