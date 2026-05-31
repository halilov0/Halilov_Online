package com.halilov.online.payment;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Payment initiation, capture-on-return, the PayPal webhook, and the mock
 * completion hook.
 *
 * <ul>
 *   <li>{@code POST /api/orders/{n}/pay} — start a checkout; returns the
 *       redirect URL (PayPal approval URL, or the mock page).</li>
 *   <li>{@code POST /api/payments/paypal/{n}/capture} — payer returned from
 *       PayPal; capture server-side and flip to PAID.</li>
 *   <li>{@code POST /api/payments/paypal/webhook} — public, signature-verified
 *       redundant confirmation.</li>
 *   <li>{@code POST /api/payments/mock/{n}/complete} — mock-only PAID flip (dev).</li>
 * </ul>
 *
 * <p>The pay + capture routes accept either a bearer token or an
 * {@code X-Guest-Token}; the webhook is public but verified inside the service.
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

    @PostMapping("/api/payments/paypal/{orderNumber}/capture")
    public PaymentDtos.CaptureResponse capturePaypal(
        Authentication auth,
        @PathVariable String orderNumber,
        @RequestBody(required = false) PaymentDtos.CaptureRequest req,
        @RequestHeader(value = "X-Guest-Token", required = false) String guestToken
    ) {
        String paypalOrderId = req == null ? null : req.paypalOrderId();
        if (auth != null && auth.getName() != null) {
            return payment.captureOwn(auth.getName(), orderNumber, paypalOrderId);
        }
        requireToken(guestToken);
        return payment.captureGuest(orderNumber, guestToken, paypalOrderId);
    }

    @GetMapping("/api/payments/{orderNumber}/receipt")
    public PaymentDtos.ReceiptInfo receipt(
        Authentication auth,
        @PathVariable String orderNumber,
        @RequestHeader(value = "X-Guest-Token", required = false) String guestToken
    ) {
        if (auth != null && auth.getName() != null) {
            return payment.receiptInfoOwn(auth.getName(), orderNumber);
        }
        requireToken(guestToken);
        return payment.receiptInfoGuest(orderNumber, guestToken);
    }

    @PostMapping("/api/payments/paypal/webhook")
    public void paypalWebhook(
        @RequestBody(required = false) String rawBody,
        @RequestHeader Map<String, String> headers
    ) {
        // @RequestHeader Map keys are already lowercased by Spring; PayPalClient reads them as such.
        Map<String, String> lower = new LinkedHashMap<>();
        headers.forEach((k, v) -> lower.put(k.toLowerCase(), v));
        payment.handleWebhook(lower, rawBody);
        // Always 200 once handled/ignored — a non-2xx makes PayPal retry the delivery.
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
