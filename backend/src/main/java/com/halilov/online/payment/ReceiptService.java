package com.halilov.online.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.halilov.online.order.Order;
import com.halilov.online.order.OrderItem;
import com.halilov.online.order.OrderService;

import java.util.ArrayList;
import java.util.List;

/**
 * Issues the Green Invoice קבלה for a PAID charge and records the result via
 * {@link PaymentRecorder} (which owns the transactional row writes). Runs AFTER
 * the PAID commit and never throws back to the payment flow: a GI failure leaves
 * {@code gi_status=FAILED} for {@link ReceiptRetryJob} to retry, so the
 * customer's paid order is never blocked by a receipt outage.
 *
 * <p>The slow GI HTTP call deliberately runs outside any transaction; only the
 * short result write (delegated to the recorder) is transactional.
 */
@Service
public class ReceiptService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);

    private final GreenInvoiceClient gi;
    private final OrderService orderService;
    private final PaymentRepository payments;
    private final PaymentRecorder recorder;

    public ReceiptService(GreenInvoiceClient gi, OrderService orderService,
                          PaymentRepository payments, PaymentRecorder recorder) {
        this.gi = gi;
        this.orderService = orderService;
        this.payments = payments;
        this.recorder = recorder;
    }

    /**
     * Issue (or re-issue) the receipt for one charge. Safe to call repeatedly —
     * an already-ISSUED row is skipped. Swallows all failures into
     * {@code gi_status=FAILED}.
     */
    public void issueForPayment(Long paymentId) {
        Payment payment = payments.findById(paymentId).orElse(null);
        if (payment == null || payment.getGiStatus() == GiStatus.ISSUED) return;
        if (!gi.isConfigured()) {
            log.warn("Green Invoice not configured — leaving receipt pending for payment {}", paymentId);
            return;
        }
        try {
            OrderService.ReceiptContext ctx = orderService.receiptContext(payment.getOrderId());
            Order order = ctx.order();

            List<GreenInvoiceClient.Line> lines = new ArrayList<>();
            for (OrderItem oi : order.getItems()) {
                lines.add(new GreenInvoiceClient.Line(oi.getNameHe(), oi.getQuantity(), oi.getUnitPriceAgorot()));
            }
            GreenInvoiceClient.Receipt receipt = gi.issueReceipt(new GreenInvoiceClient.ReceiptRequest(
                ctx.buyerName(), ctx.buyerEmail(),
                lines, order.getShippingAgorot(), order.getDiscountAgorot(), order.getCouponCode(),
                order.getTotalAgorot()
            ));
            recorder.markGiIssued(paymentId, receipt.docId(), receipt.number(), gi.documentType(), receipt.url());
            log.info("Green Invoice receipt {} issued for order {}", receipt.number(), order.getOrderNumber());
        } catch (Exception e) {
            log.warn("Green Invoice receipt failed for payment {}: {}", paymentId, e.toString());
            recorder.markGiFailed(paymentId);
        }
    }
}
