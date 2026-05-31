package com.halilov.online.payment;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.halilov.online.order.Order;
import com.halilov.online.order.OrderRepository;
import com.halilov.online.order.OrderService;

import java.time.Instant;
import java.util.Optional;

/**
 * Owns all {@code payments}-table writes and the transactional PAID confirmation.
 * Kept separate from {@link PaymentService} (the orchestrator) so its
 * {@code @Transactional} boundaries apply through the Spring proxy — the
 * orchestrator runs the slow gateway HTTP calls outside any open transaction.
 */
@Service
public class PaymentRecorder {

    private final PaymentRepository payments;
    private final OrderService orderService;
    private final OrderRepository orders;

    public PaymentRecorder(PaymentRepository payments, OrderService orderService, OrderRepository orders) {
        this.payments = payments;
        this.orderService = orderService;
        this.orders = orders;
    }

    /** Inserts the INITIATED charge row when a PayPal order is created. */
    @Transactional
    public Payment startCharge(Order order, String providerOrderId, int amountAgorot) {
        Payment p = new Payment();
        p.setOrderId(order.getId());
        p.setProvider("paypal");
        p.setKind(PaymentKind.CHARGE);
        p.setStatus(PaymentStatus.INITIATED);
        p.setAmountAgorot(amountAgorot);
        p.setCurrency("ILS");
        p.setProviderOrderId(providerOrderId);
        return payments.save(p);
    }

    public Optional<Payment> latestCharge(Long orderId) {
        return payments.findFirstByOrderIdAndKindOrderByIdDesc(orderId, PaymentKind.CHARGE);
    }

    public Optional<Payment> chargeByPaypalOrderId(String providerOrderId) {
        return payments.findFirstByProviderOrderIdOrderByIdDesc(providerOrderId);
    }

    @Transactional
    public void markChargeFailed(Long paymentId) {
        payments.findById(paymentId).ifPresent(p -> {
            p.setStatus(PaymentStatus.FAILED);
            payments.save(p);
        });
    }

    /**
     * Records a successfully issued Green Invoice קבלה on the charge row and
     * mirrors the document number onto the order. Runs in a real transaction
     * (separate bean → proxy applies), so the order update persists via
     * dirty-checking — both writes commit atomically.
     */
    @Transactional
    public void markGiIssued(Long paymentId, String docId, String number, Integer docType, String url) {
        Payment p = payments.findById(paymentId).orElse(null);
        if (p == null) return;
        p.setGiStatus(GiStatus.ISSUED);
        p.setGiDocId(docId);
        p.setGiDocNumber(number);
        p.setGiDocType(docType);
        p.setGiDocUrl(url);
        if (number != null) {
            // Managed in this transaction — dirty-checking flushes the mirror on commit.
            orders.findById(p.getOrderId()).ifPresent(o -> o.setInvoiceNumber(number));
        }
    }

    @Transactional
    public void markGiFailed(Long paymentId) {
        payments.findById(paymentId).ifPresent(p -> p.setGiStatus(GiStatus.FAILED));
    }

    public enum ConfirmOutcome { CONFIRMED, ALREADY }

    public record ConfirmResult(ConfirmOutcome outcome, Long paymentId) {}

    /**
     * Idempotently flips the order to PAID and marks the charge row PAID, in one
     * transaction. Guarded by the {@code (provider, provider_txn_id)} unique
     * index so a replayed return/webhook can't double-charge or double-issue.
     * The Green Invoice receipt is issued AFTER this commits (caller's job).
     */
    @Transactional
    public ConfirmResult confirmPaid(String orderNumber, String providerOrderId, String captureId, String rawJson) {
        if (captureId != null && payments.existsByProviderAndProviderTxnId("paypal", captureId)) {
            return new ConfirmResult(ConfirmOutcome.ALREADY, null);
        }
        // Flip the order first (decrements stock, sends the paid email, bumps coupon).
        orderService.markPaidByPayment(orderNumber, captureId);

        Payment p = (providerOrderId != null
            ? payments.findFirstByProviderOrderIdOrderByIdDesc(providerOrderId)
            : Optional.<Payment>empty())
            .orElseGet(() -> createChargeFor(orderNumber, providerOrderId));
        p.setStatus(PaymentStatus.PAID);
        p.setProviderTxnId(captureId);
        p.setPaidAt(Instant.now());
        p.setGiStatus(GiStatus.PENDING);
        if (rawJson != null) p.setRawCallback(rawJson);
        payments.save(p);
        return new ConfirmResult(ConfirmOutcome.CONFIRMED, p.getId());
    }

    /** Webhook arrived before our INITIATED row existed — synthesize one. */
    private Payment createChargeFor(String orderNumber, String providerOrderId) {
        Order order = orders.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
        Payment p = new Payment();
        p.setOrderId(order.getId());
        p.setProvider("paypal");
        p.setKind(PaymentKind.CHARGE);
        p.setAmountAgorot(order.getTotalAgorot());
        p.setCurrency("ILS");
        p.setProviderOrderId(providerOrderId);
        return p;
    }
}
