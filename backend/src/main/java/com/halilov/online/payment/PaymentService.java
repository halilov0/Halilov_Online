package com.halilov.online.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.halilov.online.audit.AuditAction;
import com.halilov.online.audit.AuditService;
import com.halilov.online.order.Order;
import com.halilov.online.order.OrderRepository;
import com.halilov.online.order.OrderService;
import com.halilov.online.order.OrderStatus;
import com.halilov.online.user.User;
import com.halilov.online.user.UserRepository;

import java.util.Map;

/**
 * Payment orchestration across providers ({@code paypal} | {@code mock} |
 * {@code disabled}). For PayPal this is the redirect-style flow:
 * {@link #initiate} creates an Orders-v2 order and returns its payer-approval
 * URL; the SPA redirects there; on return {@link #captureOwn}/{@link #captureGuest}
 * capture server-side and flip the order to PAID; a signed {@link #handleWebhook}
 * is the belt-and-suspenders confirmation. The PAID flip + receipt issuance are
 * delegated to {@link PaymentRecorder} (transactional) and {@link ReceiptService}
 * (post-commit) so this orchestrator holds no transaction across gateway HTTP.
 *
 * <p>Slow gateway calls run outside any transaction here on purpose.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final OrderRepository orders;
    private final UserRepository users;
    private final OrderService orderService;
    private final PayPalClient paypal;
    private final PaymentRecorder recorder;
    private final ReceiptService receiptService;
    private final AuditService audit;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String provider;
    private final String returnBaseUrl;
    private final int giDocumentType;

    public PaymentService(
        OrderRepository orders,
        UserRepository users,
        OrderService orderService,
        PayPalClient paypal,
        PaymentRecorder recorder,
        ReceiptService receiptService,
        AuditService audit,
        @Value("${app.payment.provider:mock}") String provider,
        @Value("${app.payment.returnBaseUrl:}") String returnBaseUrl,
        @Value("${app.greenInvoice.documentType:400}") int giDocumentType
    ) {
        this.orders = orders;
        this.users = users;
        this.orderService = orderService;
        this.paypal = paypal;
        this.recorder = recorder;
        this.receiptService = receiptService;
        this.audit = audit;
        this.provider = provider;
        this.returnBaseUrl = returnBaseUrl == null ? "" : returnBaseUrl.replaceAll("/+$", "");
        this.giDocumentType = giDocumentType;
    }

    // ── initiate ──────────────────────────────────────────────────────────────

    public PaymentDtos.InitiateResponse initiate(String email, String orderNumber) {
        requireProviderEnabled();
        Order order = loadOwnPendingOrder(email, orderNumber);
        return respond(order);
    }

    public PaymentDtos.InitiateResponse initiateGuest(String orderNumber, String token) {
        requireProviderEnabled();
        Order order = loadGuestPendingOrder(orderNumber, token);
        return respond(order);
    }

    private PaymentDtos.InitiateResponse respond(Order order) {
        if ("paypal".equals(provider)) {
            return paypalInitiate(order);
        }
        // mock provider — in-app fake checkout page.
        return new PaymentDtos.InitiateResponse(provider, mockRedirectUrl(order), order.getOrderNumber());
    }

    private PaymentDtos.InitiateResponse paypalInitiate(Order order) {
        if (returnBaseUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "payment return URL not configured");
        }
        PayPalClient.CreatedOrder created = paypal.createOrder(
            order.getOrderNumber(), order.getTotalAgorot(),
            returnUrl(order), cancelUrl(order));
        recorder.startCharge(order, created.orderId(), order.getTotalAgorot());
        return new PaymentDtos.InitiateResponse("paypal", created.approveUrl(), order.getOrderNumber());
    }

    // ── capture on return ───────────────────────────────────────────────────────

    public PaymentDtos.CaptureResponse captureOwn(String email, String orderNumber, String paypalOrderId) {
        requirePayPalProvider();
        Order order = loadOwnOrder(email, orderNumber);
        return capture(order, paypalOrderId);
    }

    public PaymentDtos.CaptureResponse captureGuest(String orderNumber, String token, String paypalOrderId) {
        requirePayPalProvider();
        Order order = orderService.loadByGuestToken(orderNumber, token);
        return capture(order, paypalOrderId);
    }

    private PaymentDtos.CaptureResponse capture(Order order, String paypalOrderId) {
        if (order.getStatus() == OrderStatus.PAID) {
            return new PaymentDtos.CaptureResponse(order.getOrderNumber(), OrderStatus.PAID.name());
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            // Cancelled / refunded — nothing to capture, report as-is.
            return new PaymentDtos.CaptureResponse(order.getOrderNumber(), order.getStatus().name());
        }

        Payment charge = resolveCharge(order, paypalOrderId);
        PayPalClient.CaptureResult cr = paypal.captureOrder(charge.getProviderOrderId());
        if (!"COMPLETED".equalsIgnoreCase(cr.status()) || cr.captureId() == null) {
            recorder.markChargeFailed(charge.getId());
            log.warn("PayPal capture not completed for order {} (status={})", order.getOrderNumber(), cr.status());
            return new PaymentDtos.CaptureResponse(order.getOrderNumber(), order.getStatus().name());
        }

        PaymentRecorder.ConfirmResult res = recorder.confirmPaid(
            order.getOrderNumber(), charge.getProviderOrderId(), cr.captureId(), null);
        if (res.outcome() == PaymentRecorder.ConfirmOutcome.CONFIRMED && res.paymentId() != null) {
            // Receipt runs after the PAID commit — a GI failure here must not unwind the charge.
            receiptService.issueForPayment(res.paymentId());
        }
        return new PaymentDtos.CaptureResponse(order.getOrderNumber(), OrderStatus.PAID.name());
    }

    /** Pick the charge row to capture: the one PayPal returned (token), else the latest. */
    private Payment resolveCharge(Order order, String paypalOrderId) {
        Payment charge;
        if (paypalOrderId != null && !paypalOrderId.isBlank()) {
            charge = recorder.chargeByPaypalOrderId(paypalOrderId).orElse(null);
            if (charge == null || !order.getId().equals(charge.getOrderId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "payment not found for order");
            }
        } else {
            charge = recorder.latestCharge(order.getId()).orElse(null);
        }
        if (charge == null || charge.getProviderOrderId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "no payment to capture for this order");
        }
        return charge;
    }

    // ── receipt lookup (read) ───────────────────────────────────────────────────

    public PaymentDtos.ReceiptInfo receiptInfoOwn(String email, String orderNumber) {
        return receiptInfo(loadOwnOrder(email, orderNumber));
    }

    public PaymentDtos.ReceiptInfo receiptInfoGuest(String orderNumber, String token) {
        return receiptInfo(orderService.loadByGuestToken(orderNumber, token));
    }

    /** The issued GI קבלה for an order, or both-null when none is issued yet. */
    private PaymentDtos.ReceiptInfo receiptInfo(Order order) {
        return recorder.latestCharge(order.getId())
            .filter(p -> p.getGiStatus() == GiStatus.ISSUED && p.getGiDocUrl() != null)
            .map(p -> new PaymentDtos.ReceiptInfo(p.getGiDocNumber(), p.getGiDocUrl()))
            .orElse(new PaymentDtos.ReceiptInfo(null, null));
    }

    // ── admin (manual receipt) ───────────────────────────────────────────────────

    /**
     * Admin records a manually-issued Green Invoice קבלה for a PAID order. The
     * lean launch issues receipts by hand in morning's free tier (the automated
     * API needs a paid plan), so the charge sits {@code gi_status=PENDING} after
     * capture; this stores the doc number/url and flips it to {@code ISSUED}.
     * Marking ISSUED also stops {@link ReceiptRetryJob} from re-issuing it as a
     * duplicate קבלה once the GI API is later enabled.
     */
    public void adminMarkReceipt(String orderNumber, String number, String url) {
        String num = number == null ? null : number.trim();
        if (num == null || num.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "receipt number required");
        }
        Order order = orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
        Payment charge = recorder.latestCharge(order.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                "no payment recorded for this order"));
        if (charge.getStatus() != PaymentStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "order is not paid");
        }
        String link = (url == null || url.isBlank()) ? null : url.trim();
        recorder.markGiIssued(charge.getId(), null, num, giDocumentType, link);
        audit.record(AuditAction.RECEIPT_ISSUED_MANUAL, "order", orderNumber,
            "קבלה ידנית סומנה: " + num);
    }

    // ── webhook (signed, public) ──────────────────────────────────────────────

    /**
     * Verified PayPal webhook. Treated as a redundant confirmation of the
     * return-path capture — idempotent via {@link PaymentRecorder#confirmPaid}.
     * Silently ignores unverifiable or irrelevant events (returns to the caller,
     * which 200s so PayPal stops retrying).
     */
    public void handleWebhook(Map<String, String> headers, String rawBody) {
        if (rawBody == null || rawBody.isBlank()) return;
        if (!paypal.verifyWebhook(headers, rawBody)) {
            log.warn("PayPal webhook signature not verified — ignoring");
            return;
        }
        try {
            JsonNode event = mapper.readTree(rawBody);
            String type = event.path("event_type").asText("");
            JsonNode resource = event.path("resource");
            switch (type) {
                case "CHECKOUT.ORDER.APPROVED" -> {
                    String paypalOrderId = resource.path("id").asText(null);
                    String orderNumber = resource.path("purchase_units").path(0).path("custom_id").asText(null);
                    if (paypalOrderId != null && orderNumber != null) {
                        PayPalClient.CaptureResult cr = paypal.captureOrder(paypalOrderId);
                        confirmFromWebhook(orderNumber, paypalOrderId, cr.captureId(), rawBody);
                    }
                }
                case "PAYMENT.CAPTURE.COMPLETED" -> {
                    String captureId = resource.path("id").asText(null);
                    String orderNumber = resource.path("custom_id").asText(null);
                    String paypalOrderId = resource.path("supplementary_data")
                        .path("related_ids").path("order_id").asText(null);
                    if (orderNumber != null && captureId != null) {
                        confirmFromWebhook(orderNumber, paypalOrderId, captureId, rawBody);
                    }
                }
                default -> log.debug("PayPal webhook {} ignored", type);
            }
        } catch (Exception e) {
            log.warn("PayPal webhook processing failed: {}", e.toString());
        }
    }

    private void confirmFromWebhook(String orderNumber, String paypalOrderId, String captureId, String rawBody) {
        if (captureId == null) return;
        PaymentRecorder.ConfirmResult res = recorder.confirmPaid(orderNumber, paypalOrderId, captureId, rawBody);
        if (res.outcome() == PaymentRecorder.ConfirmOutcome.CONFIRMED && res.paymentId() != null) {
            receiptService.issueForPayment(res.paymentId());
        }
    }

    // ── mock provider (dev) ─────────────────────────────────────────────────────

    public PaymentDtos.InitiateResponse completeMock(String email, String orderNumber, String outcome) {
        requireMockProvider();
        Order order = loadOwnPendingOrder(email, orderNumber);
        return applyMockOutcome(order, outcome);
    }

    public PaymentDtos.InitiateResponse completeMockGuest(String orderNumber, String token, String outcome) {
        requireMockProvider();
        Order order = loadGuestPendingOrder(orderNumber, token);
        return applyMockOutcome(order, outcome);
    }

    private PaymentDtos.InitiateResponse applyMockOutcome(Order order, String outcome) {
        if ("success".equalsIgnoreCase(outcome)) {
            orderService.adminUpdateStatus(order.getOrderNumber(), OrderStatus.PAID);
        } else if ("cancel".equalsIgnoreCase(outcome)) {
            orderService.adminUpdateStatus(order.getOrderNumber(), OrderStatus.CANCELLED);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "outcome must be success|cancel");
        }
        return new PaymentDtos.InitiateResponse(provider, "/confirmation/" + order.getOrderNumber(), order.getOrderNumber());
    }

    // ── guards ────────────────────────────────────────────────────────────────

    /**
     * In prod we default {@code app.payment.provider} to {@code disabled} so
     * the mock-complete endpoint cannot be hit. Hard-stops the "anyone marks
     * their own order PAID" path until a real gateway is wired.
     */
    private void requireProviderEnabled() {
        if ("disabled".equals(provider)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "התשלום אינו זמין כרגע. נציג ייצור איתך קשר להשלמת ההזמנה.");
        }
    }

    private void requireMockProvider() {
        if (!"mock".equals(provider)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "mock complete is disabled");
        }
    }

    private void requirePayPalProvider() {
        if (!"paypal".equals(provider)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "paypal capture is disabled");
        }
    }

    // ── order loading ───────────────────────────────────────────────────────────

    private Order loadOwnPendingOrder(String email, String orderNumber) {
        Order order = loadOwnOrder(email, orderNumber);
        requirePending(order);
        return order;
    }

    private Order loadOwnOrder(String email, String orderNumber) {
        User user = users.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no user"));
        Order order = orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
        if (!user.getId().equals(order.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found");
        }
        return order;
    }

    private Order loadGuestPendingOrder(String orderNumber, String token) {
        Order order = orderService.loadByGuestToken(orderNumber, token);
        requirePending(order);
        return order;
    }

    private void requirePending(Order order) {
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "order not in PENDING state");
        }
    }

    // ── redirect URLs ───────────────────────────────────────────────────────────

    private String mockRedirectUrl(Order order) {
        String base = "/payment/mock?order=" + order.getOrderNumber();
        return order.getGuestToken() == null ? base : base + "&t=" + order.getGuestToken();
    }

    private String returnUrl(Order order) {
        return returnBaseUrl + "/payment/return?order=" + order.getOrderNumber() + guestSuffix(order);
    }

    private String cancelUrl(Order order) {
        return returnBaseUrl + "/payment/cancel?order=" + order.getOrderNumber() + guestSuffix(order);
    }

    private String guestSuffix(Order order) {
        return order.getGuestToken() == null ? "" : "&t=" + order.getGuestToken();
    }
}
