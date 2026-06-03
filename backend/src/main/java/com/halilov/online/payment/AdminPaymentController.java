package com.halilov.online.payment;

import com.halilov.online.order.OrderDtos;
import com.halilov.online.order.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only receipt action under {@code /api/admin/orders} (ADMIN-secured by
 * {@code SecurityConfig}, sharing the prefix with
 * {@link com.halilov.online.order.AdminOrderController}).
 *
 * <p>The lean launch issues the Green Invoice קבלה by hand — the automated API
 * needs a paid GI plan. After creating the receipt in morning, the admin records
 * its number (and an optional public link) here;
 * {@link PaymentService#adminMarkReceipt} marks the charge {@code ISSUED} so
 * {@link ReceiptRetryJob} won't later re-issue a duplicate once the API is
 * enabled, and the invoice page links the קבלה. Returns the refreshed order.
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminPaymentController {

    private final PaymentService payment;
    private final OrderService orderService;

    public AdminPaymentController(PaymentService payment, OrderService orderService) {
        this.payment = payment;
        this.orderService = orderService;
    }

    @PostMapping("/{orderNumber}/receipt")
    public OrderDtos.OrderView markReceipt(
        @PathVariable String orderNumber,
        @Valid @RequestBody PaymentDtos.ManualReceiptRequest req
    ) {
        payment.adminMarkReceipt(orderNumber, req.number(), req.url());
        return orderService.adminGet(orderNumber);
    }
}
