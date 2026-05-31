package com.halilov.online.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sweeps charges whose Green Invoice receipt is still {@code PENDING}/{@code FAILED}
 * and retries issuance. This is what makes "GI failure never blocks a paid order"
 * eventually consistent — the charge committed PAID, the receipt catches up here.
 * No-op when GI is unconfigured (mock/disabled dev).
 */
@Component
public class ReceiptRetryJob {

    private static final Logger log = LoggerFactory.getLogger(ReceiptRetryJob.class);

    private final PaymentRepository payments;
    private final ReceiptService receiptService;
    private final GreenInvoiceClient gi;

    public ReceiptRetryJob(PaymentRepository payments, ReceiptService receiptService, GreenInvoiceClient gi) {
        this.payments = payments;
        this.receiptService = receiptService;
        this.gi = gi;
    }

    @Scheduled(fixedDelayString = "${app.payment.receiptRetryMs:300000}", initialDelayString = "60000")
    public void sweep() {
        if (!gi.isConfigured()) return;
        List<Payment> pending = payments.findTop50ByGiStatusInOrderByCreatedAtAsc(
            List.of(GiStatus.PENDING, GiStatus.FAILED));
        if (pending.isEmpty()) return;
        log.info("receipt retry sweep: {} pending/failed", pending.size());
        for (Payment p : pending) {
            receiptService.issueForPayment(p.getId());
        }
    }
}
