package com.halilov.online.payment;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Payment initiation and the mock-provider completion hook.
 *
 * <p>Two routes:
 * <ul>
 *   <li>{@code POST /api/orders/{n}/pay} — start a checkout. Returns
 *       the redirect URL the SPA should send the user to.</li>
 *   <li>{@code POST /api/payments/mock/{n}/complete} — the mock-only
 *       completion callback that flips an order to {@code PAID}. A real
 *       PSP integration replaces this with a signed webhook.</li>
 * </ul>
 *
 * <p>Both accept either an authenticated bearer token or an
 * {@code X-Guest-Token} header. Anonymous calls without a valid token
 * get {@code 401}.
 */
@RestController
public class PaymentController {

    private final PaymentService payment;

    public PaymentController(PaymentService payment) {
        this.payment = payment;
    }

    @PostMapping("/api/orders/{orderNumber}/pay")
    public PaymentDtos.InitiateResponse initiate(
        Authentication auth,
        @PathVariable String orderNumber,
        @RequestHeader(value = "X-Guest-Token", required = false) String guestToken
    ) {
        if (auth != null && auth.getName() != null) {
            return payment.initiate(auth.getName(), orderNumber);
        }
        requireToken(guestToken);
        return payment.initiateGuest(orderNumber, guestToken);
    }

    @PostMapping("/api/payments/mock/{orderNumber}/complete")
    public PaymentDtos.InitiateResponse completeMock(
        Authentication auth,
        @PathVariable String orderNumber,
        @RequestBody PaymentDtos.MockCompleteRequest req,
        @RequestHeader(value = "X-Guest-Token", required = false) String guestToken
    ) {
        if (auth != null && auth.getName() != null) {
            return payment.completeMock(auth.getName(), orderNumber, req.outcome());
        }
        requireToken(guestToken);
        return payment.completeMockGuest(orderNumber, guestToken, req.outcome());
    }

    private void requireToken(String guestToken) {
        if (guestToken == null || guestToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
    }
}
